package com.openclaw.manager.openclawserversmanager.ssh.service;

import com.openclaw.manager.openclawserversmanager.common.exception.SshConnectionException;
import com.openclaw.manager.openclawserversmanager.secrets.service.SecretService;
import com.openclaw.manager.openclawserversmanager.servers.entity.Server;
import com.openclaw.manager.openclawserversmanager.ssh.config.SshConfig;
import com.openclaw.manager.openclawserversmanager.ssh.model.CommandResult;
import com.openclaw.manager.openclawserversmanager.ssh.model.TestConnectionResult;
import com.openclaw.manager.openclawserversmanager.ssh.model.SshSession;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import net.schmizz.sshj.userauth.keyprovider.KeyProvider;
import net.schmizz.sshj.xfer.InMemorySourceFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

@Service
public class SshService {

    private static final Logger log = LoggerFactory.getLogger(SshService.class);

    private final SecretService secretService;
    private final SshConfig sshConfig;

    public SshService(SecretService secretService, SshConfig sshConfig) {
        this.secretService = secretService;
        this.sshConfig = sshConfig;
    }

    public TestConnectionResult testConnection(Server server) {
        if (server.getCredentialId() == null) {
            return new TestConnectionResult(false, "No credential configured for this server", -1);
        }

        long start = System.currentTimeMillis();
        try (SSHClient ssh = createClient(server)) {
            long latency = System.currentTimeMillis() - start;
            return new TestConnectionResult(true, "Connected successfully", latency);
        } catch (Exception e) {
            log.debug("Connection test failed for server {}: {}", server.getName(), e.getMessage());
            return new TestConnectionResult(false, e.getMessage(), -1);
        }
    }

    public CommandResult executeCommand(Server server, String command) {
        return executeCommand(server, command, sshConfig.getCommandTimeout());
    }

    public CommandResult executeCommand(Server server, String command, long timeoutSeconds) {
        if (server.getCredentialId() == null) {
            throw new SshConnectionException("No credential configured for server '" + server.getName() + "'");
        }

        long startTime = System.currentTimeMillis();
        try (SSHClient ssh = createClient(server)) {
            try (Session session = ssh.startSession()) {
                Session.Command cmd = session.exec(command);

                String stdout = readStream(cmd.getInputStream(), sshConfig.getMaxOutputSize());
                String stderr = readStream(cmd.getErrorStream(), sshConfig.getMaxOutputSize());

                cmd.join(timeoutSeconds, TimeUnit.SECONDS);

                long durationMs = System.currentTimeMillis() - startTime;
                Integer exitCode = cmd.getExitStatus();

                return new CommandResult(
                        exitCode != null ? exitCode : -1,
                        stdout,
                        stderr,
                        durationMs
                );
            }
        } catch (SshConnectionException e) {
            throw e;
        } catch (IOException e) {
            throw new SshConnectionException(
                    "SSH error on %s:%d — %s".formatted(server.getHostname(), server.getSshPort(), e.getMessage()), e);
        }
    }

    public void uploadFile(Server server, byte[] content, String remotePath) {
        validateRemotePath(remotePath);

        if (server.getCredentialId() == null) {
            throw new SshConnectionException("No credential configured for server '" + server.getName() + "'");
        }

        try (SSHClient ssh = createClient(server)) {
            try (SFTPClient sftp = ssh.newSFTPClient()) {
                String dir = remotePath.contains("/") ? remotePath.substring(0, remotePath.lastIndexOf('/')) : ".";
                String fileName = remotePath.contains("/") ? remotePath.substring(remotePath.lastIndexOf('/') + 1) : remotePath;

                sftp.put(new InMemorySourceFile() {
                    @Override public String getName() { return fileName; }
                    @Override public long getLength() { return content.length; }
                    @Override public InputStream getInputStream() { return new ByteArrayInputStream(content); }
                }, remotePath);
            }
        } catch (SshConnectionException e) {
            throw e;
        } catch (IOException e) {
            throw new SshConnectionException(
                    "SFTP upload failed on %s — %s".formatted(server.getHostname(), e.getMessage()), e);
        }
    }

    public byte[] downloadFile(Server server, String remotePath) {
        validateRemotePath(remotePath);

        if (server.getCredentialId() == null) {
            throw new SshConnectionException("No credential configured for server '" + server.getName() + "'");
        }

        try (SSHClient ssh = createClient(server)) {
            try (SFTPClient sftp = ssh.newSFTPClient()) {
                try (net.schmizz.sshj.sftp.RemoteFile remoteFile = sftp.open(remotePath)) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int totalRead = 0;
                    long offset = 0;
                    int bytesRead;
                    try (InputStream is = remoteFile.new ReadAheadRemoteFileInputStream(16)) {
                        while ((bytesRead = is.read(buffer)) != -1) {
                            if (totalRead + bytesRead > sshConfig.getMaxOutputSize()) {
                                throw new SshConnectionException(
                                        "File exceeds maximum size limit of %d bytes".formatted(sshConfig.getMaxOutputSize()));
                            }
                            baos.write(buffer, 0, bytesRead);
                            totalRead += bytesRead;
                        }
                    }
                    return baos.toByteArray();
                }
            }
        } catch (SshConnectionException e) {
            throw e;
        } catch (IOException e) {
            throw new SshConnectionException(
                    "SFTP download failed on %s — %s".formatted(server.getHostname(), e.getMessage()), e);
        }
    }

    public SshSession openInteractiveSession(Server server, int cols, int rows) {
        if (server.getCredentialId() == null) {
            throw new SshConnectionException("No credential configured for server '" + server.getName() + "'");
        }

        SSHClient ssh = null;
        try {
            ssh = createClient(server);
            Session session = ssh.startSession();
            session.allocatePTY("xterm-256color", cols, rows, 0, 0, Collections.emptyMap());
            Session.Shell shell = session.startShell();
            return new SshSession(ssh, session, shell);
        } catch (Exception e) {
            if (ssh != null) {
                try { ssh.close(); } catch (IOException ignored) {}
            }
            throw new SshConnectionException(
                    "Failed to open interactive session on %s:%d — %s".formatted(
                            server.getHostname(), server.getSshPort(), e.getMessage()), e);
        }
    }

    private SSHClient createClient(Server server) throws IOException {
        SSHClient ssh = new SSHClient();

        if (!sshConfig.isStrictHostKeyChecking()) {
            ssh.addHostKeyVerifier(new PromiscuousVerifier());
        }

        ssh.setConnectTimeout(sshConfig.getConnectionTimeout());
        ssh.connect(server.getHostname(), server.getSshPort());

        try {
            String decryptedCredential = secretService.decryptSecret(server.getCredentialId());

            switch (server.getAuthType()) {
                case PASSWORD -> ssh.authPassword(server.getSshUsername(), decryptedCredential);
                case PRIVATE_KEY -> {
                    String passphrase = null;
                    if (server.getPassphraseCredentialId() != null) {
                        passphrase = secretService.decryptSecret(server.getPassphraseCredentialId());
                    }
                    KeyProvider keyProvider = passphrase != null
                            ? ssh.loadKeys(decryptedCredential, null, net.schmizz.sshj.userauth.password.PasswordUtils.createOneOff(passphrase.toCharArray()))
                            : ssh.loadKeys(decryptedCredential, null, null);
                    ssh.authPublickey(server.getSshUsername(), keyProvider);
                }
            }
        } catch (Exception e) {
            try { ssh.close(); } catch (IOException ignored) {}
            throw e instanceof IOException ioe ? ioe : new IOException("Authentication failed: " + e.getMessage(), e);
        }

        return ssh;
    }

    private String readStream(InputStream is, int maxBytes) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int totalRead = 0;
        int bytesRead;
        while ((bytesRead = is.read(buffer)) != -1) {
            if (totalRead + bytesRead > maxBytes) {
                baos.write(buffer, 0, maxBytes - totalRead);
                break;
            }
            baos.write(buffer, 0, bytesRead);
            totalRead += bytesRead;
        }
        return baos.toString(StandardCharsets.UTF_8);
    }

    private void validateRemotePath(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Remote path must not be blank");
        }
        if (path.contains("..")) {
            throw new IllegalArgumentException("Remote path must not contain '..' (path traversal)");
        }
    }
}
