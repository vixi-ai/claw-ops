package com.openclaw.manager.openclawserversmanager.ssh.service;

import com.openclaw.manager.openclawserversmanager.servers.entity.Server;
import com.openclaw.manager.openclawserversmanager.ssh.model.CommandResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OS-aware package install via SSH. Detects the remote distro via {@code /etc/os-release}
 * and dispatches to the appropriate package manager:
 *
 * <ul>
 *   <li>Debian family (debian, ubuntu, raspbian): {@code apt-get install}</li>
 *   <li>RHEL family (rhel, centos, fedora, almalinux, rocky, ol): {@code dnf} → {@code yum}</li>
 *   <li>Alpine: {@code apk add}</li>
 *   <li>Arch family: {@code pacman -S}</li>
 *   <li>openSUSE / SLES: {@code zypper install}</li>
 * </ul>
 *
 * <p>Certbot has a special path: on Ubuntu minimal images the {@code apt} package may not be
 * in enabled repos (universe disabled on some cloud templates). If certbot is still missing
 * after the primary install attempt, we retry via snap and symlink it into {@code /usr/local/bin}
 * so {@code sudo} picks it up via {@code secure_path}.
 */
@Service
public class SystemPackageService {

    private static final Logger log = LoggerFactory.getLogger(SystemPackageService.class);
    private static final Duration DISTRO_CACHE_TTL = Duration.ofMinutes(10);
    private static final int INSTALL_TIMEOUT_SECONDS = 600;

    private final SshService sshService;
    private final Map<String, CachedDistro> distroCache = new ConcurrentHashMap<>();

    public SystemPackageService(SshService sshService) {
        this.sshService = sshService;
    }

    /**
     * Ensures the given binary commands are present on the server. Returns a detailed
     * result — caller decides what to do with partial failures.
     *
     * @param server  the target server
     * @param commands the binary names expected on PATH after install
     *                 (e.g. {@code "nginx"}, {@code "certbot"})
     */
    public InstallResult ensureInstalled(Server server, String... commands) {
        DistroInfo distro = detectDistro(server);
        Family family = familyOf(distro);
        Map<String, Status> perCommand = new LinkedHashMap<>();
        StringBuilder rawOut = new StringBuilder();

        // 1. Probe which commands are already present.
        List<String> missing = probeMissing(server, commands, perCommand, rawOut);
        if (missing.isEmpty()) {
            return new InstallResult(true, distro.raw(), perCommand, rawOut.toString(), null);
        }

        // 2. Dispatch to the right package manager for the missing commands.
        if (family == Family.UNKNOWN) {
            String msg = "Unsupported distro '" + distro.id() + "' — install nginx + certbot manually and retry.";
            log.warn("Package install skipped for server '{}': {}", server.getName(), msg);
            missing.forEach(c -> perCommand.put(c, Status.FAILED));
            return new InstallResult(false, distro.raw(), perCommand, rawOut.toString(), msg);
        }

        List<String> packages = packagesFor(family, missing);
        String installCmd = buildInstallCommand(family, packages);
        log.info("Installing on '{}' ({}): {}", server.getName(), distro.id(), packages);
        rawOut.append("\n$ ").append(installCmd).append("\n");

        CommandResult installRes = sshService.executeCommand(server, installCmd, INSTALL_TIMEOUT_SECONDS);
        rawOut.append(installRes.stdout());
        if (installRes.stderr() != null && !installRes.stderr().isBlank()) {
            rawOut.append("\n[stderr]\n").append(installRes.stderr());
        }

        // 3. Re-probe and mark per-command status.
        List<String> stillMissing = probeMissing(server, commands, perCommand, rawOut);

        // 4. Certbot snap fallback — Ubuntu 22.04 minimal sometimes has no apt-get certbot package.
        if (stillMissing.contains("certbot")) {
            log.info("certbot still missing after {} install — trying snap fallback on '{}'",
                    family, server.getName());
            String snapCmd =
                "command -v snap >/dev/null 2>&1 || { echo 'SNAP_MISSING'; exit 1; }; " +
                "sudo snap install --classic certbot && " +
                "sudo ln -sf /snap/bin/certbot /usr/local/bin/certbot && " +
                "echo 'SNAP_CERTBOT_OK'";
            rawOut.append("\n$ ").append(snapCmd).append("\n");
            CommandResult snapRes = sshService.executeCommand(server, snapCmd, INSTALL_TIMEOUT_SECONDS);
            rawOut.append(snapRes.stdout());
            if (snapRes.stderr() != null && !snapRes.stderr().isBlank()) {
                rawOut.append("\n[stderr]\n").append(snapRes.stderr());
            }
            stillMissing = probeMissing(server, commands, perCommand, rawOut);
        }

        boolean ok = stillMissing.isEmpty();
        String err = ok ? null
                : "Failed to install required packages on " + server.getName()
                        + " (distro " + distro.id() + "): still missing " + stillMissing
                        + ". Check job logs for command output.";
        if (!ok) {
            log.error("Install failed for server '{}': {}", server.getName(), stillMissing);
        }
        return new InstallResult(ok, distro.raw(), perCommand, rawOut.toString(), err);
    }

    /* ---------------------------------------------------------------- */
    /*  Internals                                                        */
    /* ---------------------------------------------------------------- */

    private List<String> probeMissing(Server server, String[] commands, Map<String, Status> out, StringBuilder raw) {
        StringBuilder sb = new StringBuilder();
        for (String c : commands) {
            sb.append("command -v ").append(c).append(" >/dev/null 2>&1 && echo '").append(c).append(":OK' || echo '")
              .append(c).append(":MISSING'; ");
        }
        CommandResult r = sshService.executeCommand(server, sb.toString(), 30);
        raw.append("\n$ probe: ").append(sb).append("\n").append(r.stdout());

        java.util.ArrayList<String> missing = new java.util.ArrayList<>();
        for (String c : commands) {
            boolean ok = r.stdout() != null && r.stdout().contains(c + ":OK");
            Status prior = out.get(c);
            if (ok) {
                out.put(c, prior == Status.INSTALLED ? Status.INSTALLED : Status.ALREADY_PRESENT);
            } else {
                // First probe: mark missing. Later probes: if still missing after install attempt, mark FAILED.
                out.put(c, prior == null ? Status.ALREADY_PRESENT /* will be updated below if missing */ : Status.FAILED);
                out.put(c, Status.FAILED);
                missing.add(c);
            }
        }
        // For first-probe accuracy: if missing on first probe, status should be INSTALLED when we later succeed.
        // We update to ALREADY_PRESENT only when already-OK on the very first call.
        for (String c : commands) {
            if (out.get(c) == Status.ALREADY_PRESENT && missing.contains(c)) {
                out.put(c, Status.FAILED);
            }
        }
        return missing;
    }

    private DistroInfo detectDistro(Server server) {
        CachedDistro cached = distroCache.get(server.getId().toString());
        if (cached != null && Instant.now().isBefore(cached.expiresAt())) {
            return cached.info();
        }
        CommandResult r = sshService.executeCommand(server,
                ". /etc/os-release 2>/dev/null && echo \"ID=$ID;ID_LIKE=$ID_LIKE;VERSION_ID=$VERSION_ID\" || echo 'ID=unknown;ID_LIKE=;VERSION_ID='",
                15);
        String out = r.stdout() == null ? "" : r.stdout().trim();
        String id = extract(out, "ID");
        String idLike = extract(out, "ID_LIKE");
        String versionId = extract(out, "VERSION_ID");
        DistroInfo info = new DistroInfo(
                id.isBlank() ? "unknown" : id.toLowerCase(),
                idLike.toLowerCase(),
                versionId,
                out);
        distroCache.put(server.getId().toString(),
                new CachedDistro(info, Instant.now().plus(DISTRO_CACHE_TTL)));
        log.debug("Detected distro for '{}': {}", server.getName(), info);
        return info;
    }

    private static String extract(String haystack, String key) {
        for (String token : haystack.split("[;\\n]")) {
            String t = token.trim();
            if (t.startsWith(key + "=")) {
                return t.substring(key.length() + 1).trim().replace("\"", "");
            }
        }
        return "";
    }

    private static Family familyOf(DistroInfo info) {
        Set<String> deb = Set.of("debian", "ubuntu", "raspbian", "linuxmint", "pop", "kali", "elementary");
        Set<String> rhel = Set.of("rhel", "centos", "fedora", "almalinux", "rocky", "ol", "amzn");
        Set<String> suse = Set.of("opensuse", "opensuse-leap", "opensuse-tumbleweed", "sles");
        Set<String> alpine = Set.of("alpine");
        Set<String> arch = Set.of("arch", "manjaro", "endeavouros", "garuda");

        String id = info.id();
        String like = info.idLike();
        if (deb.contains(id) || contains(like, deb)) return Family.DEBIAN;
        if (rhel.contains(id) || contains(like, rhel)) return Family.RHEL;
        if (alpine.contains(id) || contains(like, alpine)) return Family.ALPINE;
        if (arch.contains(id) || contains(like, arch)) return Family.ARCH;
        if (suse.contains(id) || contains(like, suse) || id.startsWith("opensuse")) return Family.SUSE;
        return Family.UNKNOWN;
    }

    private static boolean contains(String idLike, Set<String> needles) {
        if (idLike == null || idLike.isBlank()) return false;
        for (String token : idLike.split("\\s+")) {
            if (needles.contains(token.trim())) return true;
        }
        return false;
    }

    private static List<String> packagesFor(Family family, List<String> commands) {
        java.util.ArrayList<String> pkgs = new java.util.ArrayList<>();
        for (String c : commands) {
            switch (c) {
                case "nginx" -> pkgs.add("nginx");
                case "certbot" -> {
                    switch (family) {
                        case DEBIAN -> { pkgs.add("certbot"); pkgs.add("python3-certbot-nginx"); }
                        case RHEL   -> { pkgs.add("certbot"); pkgs.add("python3-certbot-nginx"); }
                        case ALPINE -> { pkgs.add("certbot"); pkgs.add("certbot-nginx"); }
                        case ARCH   -> { pkgs.add("certbot"); pkgs.add("certbot-nginx"); }
                        case SUSE   -> { pkgs.add("python3-certbot"); pkgs.add("python3-certbot-nginx"); }
                        default     -> pkgs.add(c);
                    }
                }
                default -> pkgs.add(c);
            }
        }
        // De-dup while preserving order
        return pkgs.stream().distinct().toList();
    }

    private static String buildInstallCommand(Family family, List<String> packages) {
        String joined = String.join(" ", packages);
        return switch (family) {
            case DEBIAN -> "sudo DEBIAN_FRONTEND=noninteractive apt-get update -qq && "
                    + "sudo DEBIAN_FRONTEND=noninteractive apt-get install -y -qq " + joined;
            case RHEL -> "( command -v dnf >/dev/null 2>&1 && sudo dnf install -y " + joined + " ) "
                    + "|| sudo yum install -y " + joined;
            case ALPINE -> "sudo apk add --no-cache " + joined;
            case ARCH -> "sudo pacman -S --noconfirm --needed " + joined;
            case SUSE -> "sudo zypper --non-interactive install -y " + joined;
            case UNKNOWN -> "echo 'UNSUPPORTED_DISTRO' >&2; exit 1";
        };
    }

    /* ---------------------------------------------------------------- */
    /*  Types                                                            */
    /* ---------------------------------------------------------------- */

    public enum Family { DEBIAN, RHEL, ALPINE, ARCH, SUSE, UNKNOWN }

    public enum Status { ALREADY_PRESENT, INSTALLED, FAILED }

    public record DistroInfo(String id, String idLike, String versionId, String raw) {
        @Override public String toString() { return id + (idLike.isBlank() ? "" : " (" + idLike + ")") + " " + versionId; }
    }

    public record InstallResult(
            boolean success,
            String distroId,
            Map<String, Status> perCommand,
            String rawOutput,
            String errorMessage) {
        public InstallResult {
            perCommand = perCommand == null ? Collections.emptyMap() : Map.copyOf(perCommand);
        }
    }

    private record CachedDistro(DistroInfo info, Instant expiresAt) {}

    // Suppress unused-import warning for Arrays used in test scaffolding
    @SuppressWarnings("unused")
    private static final List<String> DEBUG_FAMILIES = Arrays.stream(Family.values()).map(Enum::name).toList();
}
