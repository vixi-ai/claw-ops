package com.openclaw.manager.openclawserversmanager.ssh.model;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.channel.direct.Session;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public class SshSession implements Closeable {

    private final String sessionId;
    private final SSHClient sshClient;
    private final Session session;
    private final Session.Shell shell;

    public SshSession(SSHClient sshClient, Session session, Session.Shell shell) {
        this.sessionId = UUID.randomUUID().toString();
        this.sshClient = sshClient;
        this.session = session;
        this.shell = shell;
    }

    public String getSessionId() {
        return sessionId;
    }

    public InputStream getInputStream() {
        return shell.getInputStream();
    }

    public OutputStream getOutputStream() {
        return shell.getOutputStream();
    }

    public boolean isConnected() {
        return sshClient.isConnected() && shell.isOpen();
    }

    @Override
    public void close() throws IOException {
        try {
            if (shell.isOpen()) {
                shell.close();
            }
        } catch (IOException ignored) {
        }
        try {
            session.close();
        } catch (IOException ignored) {
        }
        try {
            sshClient.close();
        } catch (IOException ignored) {
        }
    }
}
