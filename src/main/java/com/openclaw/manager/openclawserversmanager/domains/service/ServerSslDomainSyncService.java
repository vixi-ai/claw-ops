package com.openclaw.manager.openclawserversmanager.domains.service;

import com.openclaw.manager.openclawserversmanager.domains.config.SslConfig;
import com.openclaw.manager.openclawserversmanager.domains.entity.AssignmentStatus;
import com.openclaw.manager.openclawserversmanager.domains.entity.AssignmentType;
import com.openclaw.manager.openclawserversmanager.domains.entity.DnsRecordType;
import com.openclaw.manager.openclawserversmanager.domains.entity.DomainAssignment;
import com.openclaw.manager.openclawserversmanager.domains.entity.ManagedZone;
import com.openclaw.manager.openclawserversmanager.domains.entity.SslCertificate;
import com.openclaw.manager.openclawserversmanager.domains.entity.SslStatus;
import com.openclaw.manager.openclawserversmanager.domains.repository.DomainAssignmentRepository;
import com.openclaw.manager.openclawserversmanager.domains.repository.ManagedZoneRepository;
import com.openclaw.manager.openclawserversmanager.domains.repository.SslCertificateRepository;
import com.openclaw.manager.openclawserversmanager.servers.entity.Server;
import com.openclaw.manager.openclawserversmanager.servers.repository.ServerRepository;
import com.openclaw.manager.openclawserversmanager.ssh.model.CommandResult;
import com.openclaw.manager.openclawserversmanager.ssh.service.SshService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ServerSslDomainSyncService {

    private static final Logger log = LoggerFactory.getLogger(ServerSslDomainSyncService.class);
    private static final int DISCOVERY_TIMEOUT_SECONDS = 30;
    private static final Pattern NGINX_SERVER_NAME_PATTERN =
            Pattern.compile("^\\s*server_name\\s+([^;]+);", Pattern.MULTILINE);
    private static final Pattern APACHE_NAME_VHOST_PATTERN =
            Pattern.compile("\\bnamevhost\\s+([^\\s]+)");
    private static final Pattern APACHE_ALIAS_PATTERN =
            Pattern.compile("^\\s*alias\\s+([^\\s]+)", Pattern.MULTILINE);
    private static final Pattern APACHE_DEFAULT_SERVER_PATTERN =
            Pattern.compile("\\bdefault server\\s+([^\\s]+)");
    private static final Pattern CERTBOT_EXPIRY_PATTERN =
            Pattern.compile("Expiry Date:\\s*(\\d{4}-\\d{2}-\\d{2})");
    private static final Pattern CADDY_HOST_ARRAY_PATTERN =
            Pattern.compile("\"host\"\\s*:\\s*\\[(.*?)]", Pattern.DOTALL);
    private static final Pattern TRAEFIK_HOST_RULE_PATTERN =
            Pattern.compile("Host(?:Regexp)?\\s*\\((.*?)\\)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern QUOTED_TOKEN_PATTERN =
            Pattern.compile("`([^`]+)`|\"([^\"]+)\"|'([^']+)'");

    private final SshService sshService;
    private final SslVerificationService sslVerificationService;
    private final ServerRepository serverRepository;
    private final ManagedZoneRepository managedZoneRepository;
    private final DomainAssignmentRepository domainAssignmentRepository;
    private final SslCertificateRepository sslCertificateRepository;
    private final SslConfig sslConfig;

    public ServerSslDomainSyncService(SshService sshService,
                                      SslVerificationService sslVerificationService,
                                      ServerRepository serverRepository,
                                      ManagedZoneRepository managedZoneRepository,
                                      DomainAssignmentRepository domainAssignmentRepository,
                                      SslCertificateRepository sslCertificateRepository,
                                      SslConfig sslConfig) {
        this.sshService = sshService;
        this.sslVerificationService = sslVerificationService;
        this.serverRepository = serverRepository;
        this.managedZoneRepository = managedZoneRepository;
        this.domainAssignmentRepository = domainAssignmentRepository;
        this.sslCertificateRepository = sslCertificateRepository;
        this.sslConfig = sslConfig;
    }

    @Transactional
    public SyncResult sync(Server server) {
        String currentHostname = buildAssignedDomain(server);
        DomainAssignment trackedAssignment = findPreferredAssignment(server, currentHostname);
        SslCertificate trackedCertificate = findPreferredCertificate(server, currentHostname, trackedAssignment);

        InspectionSnapshot snapshot = inspectServer(server, currentHostname, trackedAssignment, trackedCertificate);
        String selectedHostname = chooseHostname(snapshot, currentHostname);
        String persistedHostname = firstNonBlank(
                selectedHostname,
                currentHostname,
                trackedAssignment != null ? trackedAssignment.getHostname() : null,
                trackedCertificate != null ? trackedCertificate.getHostname() : null
        );

        boolean changed = applyHostname(server, persistedHostname);

        DomainAssignment assignment = trackedAssignment;
        if (persistedHostname != null && (selectedHostname != null || trackedAssignment != null || trackedCertificate != null)) {
            assignment = upsertAssignment(server, persistedHostname, trackedAssignment);
        }

        CandidateAssessment selectedAssessment = selectedHostname != null
                ? snapshot.findAssessment(selectedHostname).orElse(null)
                : null;
        SslCertificate certificate = syncCertificate(server, persistedHostname, assignment, trackedCertificate, selectedAssessment);

        boolean sslEnabled = isSslEnabled(selectedAssessment, certificate, trackedCertificate);
        if (server.isSslEnabled() != sslEnabled) {
            server.setSslEnabled(sslEnabled);
            changed = true;
        }

        if (changed) {
            serverRepository.save(server);
        }

        return new SyncResult(
                persistedHostname,
                sslEnabled,
                assignment != null ? assignment.getId() : null,
                certificate != null ? certificate.getId() : null,
                selectedHostname != null
        );
    }

    public boolean hasExternalConflict(Server server, String hostname) {
        return classifyHostname(server, hostname) == HostnameOwnership.CONFLICT;
    }

    private HostnameOwnership classifyHostname(Server server, String hostname) {
        String normalized = normalizeHostname(hostname);
        if (!isUsableHostname(normalized)) {
            return HostnameOwnership.AVAILABLE;
        }

        if (isTrackedByServer(normalized, server.getId())) {
            return HostnameOwnership.OWNED_BY_SERVER;
        }
        if (isTrackedByAnotherServer(normalized, server.getId())) {
            return HostnameOwnership.CONFLICT;
        }

        Set<String> resolvedAddresses = resolveHostnameAddresses(normalized);
        if (resolvedAddresses.isEmpty()) {
            return HostnameOwnership.AVAILABLE;
        }

        Set<String> serverAddresses = resolveServerAddresses(server);
        if (!intersects(resolvedAddresses, serverAddresses)) {
            return HostnameOwnership.CONFLICT;
        }

        CandidateSignal signal = collectCandidateSignals(server).get(normalized);
        if (signal == null || !signal.hasAnySource()) {
            return HostnameOwnership.CONFLICT;
        }

        SslVerificationService.ProbeResult probe = sslVerificationService.probe(server, normalized);
        return probe.anyReachable() ? HostnameOwnership.OWNED_BY_SERVER : HostnameOwnership.CONFLICT;
    }

    private InspectionSnapshot inspectServer(Server server,
                                             String currentHostname,
                                             DomainAssignment trackedAssignment,
                                             SslCertificate trackedCertificate) {
        Set<String> serverAddresses = resolveServerAddresses(server);
        Map<String, CandidateSignal> signals = collectCandidateSignals(server);
        List<CandidateAssessment> assessments = signals.values().stream()
                .map(signal -> assessCandidate(
                        server,
                        signal,
                        serverAddresses,
                        currentHostname,
                        trackedAssignment,
                        trackedCertificate))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(CandidateAssessment::hostname))
                .toList();
        return new InspectionSnapshot(serverAddresses, assessments);
    }

    private CandidateAssessment assessCandidate(Server server,
                                                CandidateSignal signal,
                                                Set<String> serverAddresses,
                                                String currentHostname,
                                                DomainAssignment trackedAssignment,
                                                SslCertificate trackedCertificate) {
        String hostname = signal.hostname();
        if (isTrackedByAnotherServer(hostname, server.getId())) {
            return null;
        }

        boolean trackedForServer = equalsIgnoreCase(hostname, currentHostname)
                || (trackedAssignment != null && equalsIgnoreCase(hostname, trackedAssignment.getHostname()))
                || (trackedCertificate != null && equalsIgnoreCase(hostname, trackedCertificate.getHostname()));

        Set<String> resolvedAddresses = resolveHostnameAddresses(hostname);
        boolean dnsMatchesServer = !resolvedAddresses.isEmpty() && intersects(resolvedAddresses, serverAddresses);
        if (!trackedForServer && !dnsMatchesServer) {
            return new CandidateAssessment(hostname, signal, resolvedAddresses, dnsMatchesServer,
                    trackedForServer, SslVerificationService.ProbeResult.empty());
        }

        SslVerificationService.ProbeResult probe = sslVerificationService.probe(server, hostname);
        return new CandidateAssessment(hostname, signal, resolvedAddresses, dnsMatchesServer, trackedForServer, probe);
    }

    private String chooseHostname(InspectionSnapshot snapshot, String currentHostname) {
        if (currentHostname != null) {
            Optional<CandidateAssessment> current = snapshot.findAssessment(currentHostname);
            if (current.isPresent() && current.get().isConfidentMatch()) {
                return current.get().hostname();
            }
        }

        List<CandidateAssessment> confident = snapshot.assessments().stream()
                .filter(CandidateAssessment::isConfidentMatch)
                .toList();

        if (confident.size() == 1) {
            return confident.getFirst().hostname();
        }

        List<CandidateAssessment> primaries = confident.stream()
                .filter(CandidateAssessment::isPrimaryManagedCert)
                .toList();
        if (primaries.size() == 1) {
            return primaries.getFirst().hostname();
        }

        return null;
    }

    private Map<String, CandidateSignal> collectCandidateSignals(Server server) {
        Map<String, CandidateSignal> signals = new HashMap<>();

        parseNginxServerNames(execute(server, "sudo nginx -T 2>/dev/null || true"))
                .forEach(hostname -> signal(signals, hostname).fromNginx = true);

        parseApacheServerNames(execute(server,
                "sudo apachectl -S 2>/dev/null || sudo apache2ctl -S 2>/dev/null || sudo httpd -S 2>/dev/null || true"))
                .forEach(hostname -> signal(signals, hostname).fromApache = true);

        parseCaddyServerNames(loadCaddyConfig(server))
                .forEach(hostname -> signal(signals, hostname).fromCaddy = true);

        parseTraefikServerNames(loadTraefikConfig(server))
                .forEach(hostname -> signal(signals, hostname).fromTraefik = true);

        for (ParsedCertificate certificate : parseCertificates(execute(server, "sudo certbot certificates 2>/dev/null || true"))) {
            for (String domain : certificate.domains()) {
                CandidateSignal signal = signal(signals, domain);
                signal.fromCertbot = true;
                signal.certPrimary = signal.certPrimary || equalsIgnoreCase(domain, certificate.name());
                if (certificate.expiresAt() != null
                        && (signal.certExpiry == null || certificate.expiresAt().isAfter(signal.certExpiry))) {
                    signal.certExpiry = certificate.expiresAt();
                }
            }
        }

        return signals;
    }

    private CandidateSignal signal(Map<String, CandidateSignal> signals, String hostname) {
        String normalized = normalizeHostname(hostname);
        return signals.computeIfAbsent(normalized, CandidateSignal::new);
    }

    private Set<String> parseNginxServerNames(String output) {
        Set<String> hostnames = new LinkedHashSet<>();
        Matcher matcher = NGINX_SERVER_NAME_PATTERN.matcher(output);
        while (matcher.find()) {
            Arrays.stream(matcher.group(1).trim().split("\\s+"))
                    .map(this::normalizeHostname)
                    .filter(this::isUsableHostname)
                    .forEach(hostnames::add);
        }
        return hostnames;
    }

    private Set<String> parseApacheServerNames(String output) {
        Set<String> hostnames = new LinkedHashSet<>();
        Arrays.stream(output.split("\\R")).forEach(line -> {
            Matcher nameMatcher = APACHE_NAME_VHOST_PATTERN.matcher(line);
            if (nameMatcher.find()) {
                String hostname = normalizeHostname(nameMatcher.group(1));
                if (isUsableHostname(hostname)) {
                    hostnames.add(hostname);
                }
            }

            Matcher defaultMatcher = APACHE_DEFAULT_SERVER_PATTERN.matcher(line);
            if (defaultMatcher.find()) {
                String hostname = normalizeHostname(defaultMatcher.group(1));
                if (isUsableHostname(hostname)) {
                    hostnames.add(hostname);
                }
            }
        });

        Matcher aliasMatcher = APACHE_ALIAS_PATTERN.matcher(output);
        while (aliasMatcher.find()) {
            String hostname = normalizeHostname(aliasMatcher.group(1));
            if (isUsableHostname(hostname)) {
                hostnames.add(hostname);
            }
        }
        return hostnames;
    }

    private Set<String> parseCaddyServerNames(String output) {
        Set<String> hostnames = new LinkedHashSet<>();

        Matcher jsonMatcher = CADDY_HOST_ARRAY_PATTERN.matcher(output);
        while (jsonMatcher.find()) {
            extractQuotedTokens(jsonMatcher.group(1)).stream()
                    .map(this::normalizeConfiguredHostname)
                    .filter(this::isUsableHostname)
                    .forEach(hostnames::add);
        }

        int depth = 0;
        for (String line : output.split("\\R")) {
            String trimmed = stripComment(line).trim();
            if (depth == 0 && trimmed.contains("{")) {
                String siteLabel = trimmed.substring(0, trimmed.indexOf('{')).trim();
                if (isCaddySiteLabel(siteLabel)) {
                    Arrays.stream(siteLabel.split("\\s*,\\s*"))
                            .map(this::normalizeConfiguredHostname)
                            .filter(this::isUsableHostname)
                            .forEach(hostnames::add);
                }
            }

            depth += countOccurrences(trimmed, '{');
            depth -= countOccurrences(trimmed, '}');
            if (depth < 0) {
                depth = 0;
            }
        }

        return hostnames;
    }

    private Set<String> parseTraefikServerNames(String output) {
        Set<String> hostnames = new LinkedHashSet<>();
        Matcher matcher = TRAEFIK_HOST_RULE_PATTERN.matcher(output);
        while (matcher.find()) {
            List<String> values = extractQuotedTokens(matcher.group(1));
            if (values.isEmpty()) {
                values = Arrays.stream(matcher.group(1).split(","))
                        .map(String::trim)
                        .filter(value -> !value.isBlank())
                        .toList();
            }
            values.stream()
                    .map(this::normalizeConfiguredHostname)
                    .filter(this::isUsableHostname)
                    .forEach(hostnames::add);
        }
        return hostnames;
    }

    private List<ParsedCertificate> parseCertificates(String output) {
        List<ParsedCertificate> certificates = new java.util.ArrayList<>();
        ParsedCertificateBuilder current = null;

        for (String line : output.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("Certificate Name:")) {
                if (current != null && !current.domains.isEmpty()) {
                    certificates.add(current.build());
                }
                current = new ParsedCertificateBuilder();
                current.name = normalizeHostname(trimmed.substring("Certificate Name:".length()).trim());
                continue;
            }
            if (current == null) {
                continue;
            }
            if (trimmed.startsWith("Domains:")) {
                current.domains = Arrays.stream(trimmed.substring("Domains:".length()).trim().split("\\s+"))
                        .map(this::normalizeHostname)
                        .filter(this::isUsableHostname)
                        .distinct()
                        .toList();
                continue;
            }
            if (trimmed.startsWith("Expiry Date:")) {
                Matcher matcher = CERTBOT_EXPIRY_PATTERN.matcher(trimmed);
                if (matcher.find()) {
                    current.expiresAt = Instant.parse(matcher.group(1) + "T00:00:00Z");
                }
            }
        }

        if (current != null && !current.domains.isEmpty()) {
            certificates.add(current.build());
        }

        return certificates;
    }

    private String loadCaddyConfig(Server server) {
        StringBuilder output = new StringBuilder();
        appendOutput(output, execute(server,
                "sudo caddy adapt --config /etc/caddy/Caddyfile --adapter caddyfile --pretty 2>/dev/null " +
                        "|| sudo caddy adapt --config /usr/local/etc/caddy/Caddyfile --adapter caddyfile --pretty 2>/dev/null " +
                        "|| true"));
        appendOutput(output, execute(server,
                "for f in /etc/caddy/Caddyfile /usr/local/etc/caddy/Caddyfile /etc/caddy/config.json /usr/local/etc/caddy/config.json; do " +
                        "sudo test -f \"$f\" && sudo cat \"$f\"; printf '\\n'; done 2>/dev/null || true"));
        return output.toString();
    }

    private String loadTraefikConfig(Server server) {
        StringBuilder output = new StringBuilder();
        appendOutput(output, execute(server,
                "sudo find /etc/traefik -maxdepth 3 -type f \\( -name '*.yml' -o -name '*.yaml' -o -name '*.toml' \\) " +
                        "-exec cat {} \\; 2>/dev/null || true"));
        appendOutput(output, execute(server,
                "sudo docker ps -q 2>/dev/null | xargs -r sudo docker inspect --format " +
                        "'{{range $k, $v := .Config.Labels}}{{println $k \"=\" $v}}{{end}}' 2>/dev/null || true"));
        return output.toString();
    }

    private String execute(Server server, String command) {
        try {
            CommandResult result = sshService.executeCommand(server, command, DISCOVERY_TIMEOUT_SECONDS);
            String output = result.stdout();
            if (output == null || output.isBlank()) {
                output = result.stderr();
            }
            return output != null ? output : "";
        } catch (Exception e) {
            log.debug("Remote domain discovery command failed for '{}': {}", server.getName(), e.getMessage());
            return "";
        }
    }

    private Set<String> resolveServerAddresses(Server server) {
        Set<String> addresses = new LinkedHashSet<>();
        addAddress(addresses, server.getIpAddress());
        addResolvedAddresses(addresses, server.getHostname());
        Arrays.stream(execute(server, "hostname -I 2>/dev/null || true").trim().split("\\s+"))
                .map(this::normalizeAddress)
                .filter(this::isUsableAddress)
                .forEach(addresses::add);
        return addresses;
    }

    private Set<String> resolveHostnameAddresses(String hostname) {
        Set<String> addresses = new LinkedHashSet<>();
        if (!isUsableHostname(hostname)) {
            return addresses;
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(hostname)) {
                addAddress(addresses, address.getHostAddress());
            }
        } catch (Exception e) {
            log.debug("DNS lookup failed for {}: {}", hostname, e.getMessage());
        }
        return addresses;
    }

    private void addResolvedAddresses(Set<String> addresses, String hostname) {
        resolveHostnameAddresses(hostname).forEach(addresses::add);
    }

    private void addAddress(Set<String> addresses, String address) {
        String normalized = normalizeAddress(address);
        if (isUsableAddress(normalized)) {
            addresses.add(normalized);
        }
    }

    private boolean intersects(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return false;
        }
        Set<String> copy = new HashSet<>(left);
        copy.retainAll(right);
        return !copy.isEmpty();
    }

    private DomainAssignment findPreferredAssignment(Server server, String currentHostname) {
        return domainAssignmentRepository.findByResourceIdAndStatusNot(server.getId(), AssignmentStatus.RELEASED).stream()
                .filter(assignment -> assignment.getAssignmentType() == AssignmentType.SERVER)
                .sorted((left, right) -> {
                    boolean leftMatches = currentHostname != null && currentHostname.equalsIgnoreCase(left.getHostname());
                    boolean rightMatches = currentHostname != null && currentHostname.equalsIgnoreCase(right.getHostname());
                    if (leftMatches != rightMatches) {
                        return Boolean.compare(rightMatches, leftMatches);
                    }
                    return right.getCreatedAt().compareTo(left.getCreatedAt());
                })
                .findFirst()
                .orElse(null);
    }

    private SslCertificate findPreferredCertificate(Server server,
                                                    String currentHostname,
                                                    DomainAssignment trackedAssignment) {
        Optional<SslCertificate> byServer = sslCertificateRepository.findByServerId(server.getId());
        if (byServer.isPresent()) {
            return byServer.get();
        }

        String hostname = firstNonBlank(
                trackedAssignment != null ? trackedAssignment.getHostname() : null,
                currentHostname
        );
        if (hostname == null) {
            return null;
        }

        return sslCertificateRepository.findByHostname(hostname)
                .filter(cert -> cert.getServerId() == null || server.getId().equals(cert.getServerId()))
                .orElse(null);
    }

    private DomainAssignment upsertAssignment(Server server,
                                              String hostname,
                                              DomainAssignment trackedAssignment) {
        Optional<DomainAssignment> existingExact = domainAssignmentRepository
                .findByHostnameAndStatusNot(hostname, AssignmentStatus.RELEASED);
        if (existingExact.isPresent()) {
            DomainAssignment assignment = existingExact.get();
            if (!canAttachToServer(assignment.getResourceId(), server.getId())) {
                return trackedAssignment;
            }

            boolean changed = false;
            if (assignment.getResourceId() == null) {
                assignment.setResourceId(server.getId());
                changed = true;
            }
            if (assignment.getAssignmentType() == AssignmentType.SERVER) {
                String targetValue = resolveTarget(server);
                if (!Objects.equals(assignment.getTargetValue(), targetValue)) {
                    assignment.setTargetValue(targetValue);
                    changed = true;
                }
            }
            return changed ? domainAssignmentRepository.save(assignment) : assignment;
        }

        Optional<ManagedZone> zoneOpt = findMatchingZone(hostname);
        if (zoneOpt.isEmpty()) {
            return trackedAssignment;
        }

        ManagedZone zone = zoneOpt.get();
        DomainAssignment assignment = trackedAssignment != null
                && hostname.equalsIgnoreCase(trackedAssignment.getHostname())
                ? trackedAssignment
                : new DomainAssignment();

        assignment.setZoneId(zone.getId());
        assignment.setHostname(hostname);
        assignment.setRecordType(DnsRecordType.A);
        assignment.setTargetValue(resolveTarget(server));
        assignment.setAssignmentType(AssignmentType.SERVER);
        assignment.setResourceId(server.getId());
        assignment.setStatus(AssignmentStatus.VERIFIED);
        assignment.setDesiredStateHash(computeStateHash("A", assignment.getTargetValue(), zone.getDefaultTtl()));
        return domainAssignmentRepository.save(assignment);
    }

    private SslCertificate syncCertificate(Server server,
                                           String hostname,
                                           DomainAssignment assignment,
                                           SslCertificate trackedCertificate,
                                           CandidateAssessment selectedAssessment) {
        SslCertificate certificate = trackedCertificate;

        if (certificate == null && hostname != null) {
            certificate = sslCertificateRepository.findByHostname(hostname)
                    .filter(cert -> cert.getServerId() == null || server.getId().equals(cert.getServerId()))
                    .orElse(null);
        }

        boolean hasTlsEvidence = selectedAssessment != null && selectedAssessment.probe().tlsPresent();
        if (certificate == null && !hasTlsEvidence) {
            return null;
        }

        boolean isNew = certificate == null;
        if (certificate == null) {
            certificate = new SslCertificate();
        }

        boolean changed = isNew;
        if (!Objects.equals(certificate.getServerId(), server.getId())) {
            certificate.setServerId(server.getId());
            changed = true;
        }
        if (assignment != null && !Objects.equals(certificate.getAssignmentId(), assignment.getId())) {
            certificate.setAssignmentId(assignment.getId());
            changed = true;
        }
        if (hostname != null && !hostname.equalsIgnoreCase(certificate.getHostname())) {
            certificate.setHostname(hostname);
            changed = true;
        }
        if (certificate.getTargetPort() <= 0) {
            certificate.setTargetPort(sslConfig.getTargetPort());
            changed = true;
        }

        if (hasTlsEvidence) {
            Instant expiresAt = selectedAssessment.probe().certExpiry();
            SslStatus status = selectedAssessment.probe().tlsValid() ? SslStatus.ACTIVE : SslStatus.EXPIRED;
            if (certificate.getStatus() != status) {
                certificate.setStatus(status);
                changed = true;
            }
            if (!Objects.equals(certificate.getExpiresAt(), expiresAt)) {
                certificate.setExpiresAt(expiresAt);
                changed = true;
            }
            if (status == SslStatus.ACTIVE && certificate.getLastRenewedAt() == null) {
                certificate.setLastRenewedAt(expiresAt != null
                        ? expiresAt.minus(90, ChronoUnit.DAYS)
                        : Instant.now());
                changed = true;
            }
            if (certificate.getLastError() != null) {
                certificate.setLastError(null);
                changed = true;
            }
        }

        return changed ? sslCertificateRepository.save(certificate) : certificate;
    }

    private boolean isSslEnabled(CandidateAssessment selectedAssessment,
                                 SslCertificate certificate,
                                 SslCertificate trackedCertificate) {
        if (selectedAssessment != null && selectedAssessment.probe().tlsPresent()) {
            return selectedAssessment.probe().tlsValid();
        }
        SslCertificate source = certificate != null ? certificate : trackedCertificate;
        return source != null && source.getStatus() == SslStatus.ACTIVE;
    }

    private boolean applyHostname(Server server, String hostname) {
        if (hostname == null || hostname.isBlank()) {
            return false;
        }

        HostParts parts = splitHostname(hostname);
        boolean changed = false;

        if (!Objects.equals(server.getRootDomain(), parts.rootDomain())) {
            server.setRootDomain(parts.rootDomain());
            changed = true;
        }
        if (!Objects.equals(normalizeSubdomain(server.getSubdomain()), parts.subdomain())) {
            server.setSubdomain(parts.subdomain());
            changed = true;
        }

        return changed;
    }

    private HostParts splitHostname(String hostname) {
        String normalized = normalizeHostname(hostname);
        Optional<ManagedZone> matchingZone = findMatchingZone(normalized);
        if (matchingZone.isPresent()) {
            String zoneName = matchingZone.get().getZoneName().toLowerCase();
            if (normalized.equals(zoneName)) {
                return new HostParts(zoneName, "");
            }
            return new HostParts(zoneName, normalized.substring(0, normalized.length() - zoneName.length() - 1));
        }

        String[] labels = normalized.split("\\.");
        if (labels.length < 2) {
            return new HostParts(normalized, "");
        }

        String rootDomain = labels[labels.length - 2] + "." + labels[labels.length - 1];
        String subdomain = labels.length == 2
                ? ""
                : String.join(".", Arrays.copyOf(labels, labels.length - 2));
        return new HostParts(rootDomain, subdomain);
    }

    private Optional<ManagedZone> findMatchingZone(String hostname) {
        String normalized = normalizeHostname(hostname);
        return managedZoneRepository.findAll().stream()
                .filter(zone -> {
                    String zoneName = zone.getZoneName().toLowerCase();
                    return normalized.equals(zoneName) || normalized.endsWith("." + zoneName);
                })
                .max(Comparator.comparingInt(zone -> zone.getZoneName().length()));
    }

    private boolean isTrackedByServer(String hostname, UUID serverId) {
        return domainAssignmentRepository.findByHostnameAndStatusNot(hostname, AssignmentStatus.RELEASED)
                .filter(assignment -> serverId.equals(assignment.getResourceId()))
                .isPresent()
                || sslCertificateRepository.findByHostname(hostname)
                .filter(cert -> serverId.equals(cert.getServerId()))
                .isPresent();
    }

    private boolean isTrackedByAnotherServer(String hostname, UUID serverId) {
        return domainAssignmentRepository.findByHostnameAndStatusNot(hostname, AssignmentStatus.RELEASED)
                .filter(assignment -> assignment.getResourceId() != null && !serverId.equals(assignment.getResourceId()))
                .isPresent()
                || sslCertificateRepository.findByHostname(hostname)
                .filter(cert -> cert.getServerId() != null && !serverId.equals(cert.getServerId()))
                .isPresent();
    }

    private String buildAssignedDomain(Server server) {
        if (server.getRootDomain() == null || server.getRootDomain().isBlank()) {
            return null;
        }
        if (server.getSubdomain() == null || server.getSubdomain().isBlank()) {
            return server.getRootDomain();
        }
        return server.getSubdomain() + "." + server.getRootDomain();
    }

    private String resolveTarget(Server server) {
        return server.getIpAddress() != null && !server.getIpAddress().isBlank()
                ? server.getIpAddress()
                : server.getHostname();
    }

    private String normalizeHostname(String hostname) {
        if (hostname == null) {
            return null;
        }
        String normalized = hostname.trim().toLowerCase(Locale.ROOT);
        while (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String normalizeAddress(String address) {
        if (address == null) {
            return null;
        }
        String normalized = address.trim().toLowerCase();
        int slashIndex = normalized.indexOf('/');
        if (slashIndex >= 0) {
            normalized = normalized.substring(0, slashIndex);
        }
        int zoneIndex = normalized.indexOf('%');
        if (zoneIndex >= 0) {
            normalized = normalized.substring(0, zoneIndex);
        }
        return normalized;
    }

    private String normalizeSubdomain(String subdomain) {
        return subdomain == null ? "" : subdomain;
    }

    private boolean isUsableHostname(String hostname) {
        return hostname != null
                && hostname.contains(".")
                && !hostname.startsWith("*")
                && !hostname.startsWith(".")
                && !hostname.startsWith("~")
                && !hostname.contains("$")
                && !hostname.contains("{")
                && !hostname.contains("}")
                && !hostname.contains("[")
                && !hostname.contains("]")
                && !hostname.contains("*")
                && !hostname.contains("/")
                && !hostname.equals("_");
    }

    private boolean isUsableAddress(String address) {
        return address != null && !address.isBlank();
    }

    private boolean equalsIgnoreCase(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    private boolean canAttachToServer(UUID existingResourceId, UUID serverId) {
        return existingResourceId == null || serverId.equals(existingResourceId);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String normalizeConfiguredHostname(String token) {
        if (token == null) {
            return null;
        }

        String normalized = token.trim();
        while (!normalized.isEmpty() && ",;".indexOf(normalized.charAt(normalized.length() - 1)) >= 0) {
            normalized = normalized.substring(0, normalized.length() - 1).trim();
        }
        normalized = normalized.replaceAll("^[`'\\\"]+|[`'\\\"]+$", "");
        normalized = normalized.toLowerCase(Locale.ROOT);

        if (normalized.startsWith("http://")) {
            normalized = normalized.substring("http://".length());
        } else if (normalized.startsWith("https://")) {
            normalized = normalized.substring("https://".length());
        } else if (normalized.startsWith("h2c://")) {
            normalized = normalized.substring("h2c://".length());
        }

        int slashIndex = normalized.indexOf('/');
        if (slashIndex >= 0) {
            normalized = normalized.substring(0, slashIndex);
        }

        int atIndex = normalized.lastIndexOf('@');
        if (atIndex >= 0) {
            normalized = normalized.substring(atIndex + 1);
        }

        if (normalized.startsWith(":")) {
            return null;
        }

        int colonIndex = normalized.lastIndexOf(':');
        if (colonIndex > 0 && normalized.indexOf(':') == colonIndex) {
            String port = normalized.substring(colonIndex + 1);
            if (port.chars().allMatch(Character::isDigit)) {
                normalized = normalized.substring(0, colonIndex);
            }
        }

        if (normalized.isBlank()
                || normalized.contains(" ")
                || normalized.contains("{")
                || normalized.contains("}")
                || normalized.contains("*")) {
            return null;
        }

        return normalizeHostname(normalized);
    }

    private List<String> extractQuotedTokens(String input) {
        List<String> tokens = new java.util.ArrayList<>();
        Matcher matcher = QUOTED_TOKEN_PATTERN.matcher(input);
        while (matcher.find()) {
            String value = firstNonBlank(matcher.group(1), matcher.group(2), matcher.group(3));
            if (value != null && !value.isBlank()) {
                tokens.add(value);
            }
        }
        return tokens;
    }

    private String stripComment(String line) {
        int commentIndex = line.indexOf('#');
        return commentIndex >= 0 ? line.substring(0, commentIndex) : line;
    }

    private boolean isCaddySiteLabel(String siteLabel) {
        return !siteLabel.isBlank()
                && !siteLabel.startsWith("(")
                && !siteLabel.startsWith("@");
    }

    private int countOccurrences(String input, char target) {
        int count = 0;
        for (int i = 0; i < input.length(); i++) {
            if (input.charAt(i) == target) {
                count++;
            }
        }
        return count;
    }

    private void appendOutput(StringBuilder builder, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append(System.lineSeparator());
        }
        builder.append(value);
    }

    private String computeStateHash(String recordType, String targetValue, int ttl) {
        try {
            String input = "%s|%s|%d".formatted(recordType, targetValue, ttl);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }

    public record SyncResult(
            String hostname,
            boolean sslEnabled,
            UUID assignmentId,
            UUID certificateId,
            boolean detected
    ) {
    }

    private record InspectionSnapshot(
            Set<String> serverAddresses,
            List<CandidateAssessment> assessments
    ) {
        private Optional<CandidateAssessment> findAssessment(String hostname) {
            return assessments.stream()
                    .filter(assessment -> assessment.hostname().equalsIgnoreCase(hostname))
                    .findFirst();
        }
    }

    private record CandidateAssessment(
            String hostname,
            CandidateSignal signal,
            Set<String> resolvedAddresses,
            boolean dnsMatchesServer,
            boolean trackedForServer,
            SslVerificationService.ProbeResult probe
    ) {
        private boolean isConfidentMatch() {
            return signal.hasAnySource()
                    && (dnsMatchesServer || trackedForServer)
                    && probe.anyReachable();
        }

        private boolean isPrimaryManagedCert() {
            return signal.certPrimary && signal.hasWebConfig();
        }
    }

    private record HostParts(
            String rootDomain,
            String subdomain
    ) {
    }

    private record ParsedCertificate(
            String name,
            List<String> domains,
            Instant expiresAt
    ) {
    }

    private enum HostnameOwnership {
        OWNED_BY_SERVER,
        CONFLICT,
        AVAILABLE
    }

    private static final class CandidateSignal {
        private final String hostname;
        private boolean fromNginx;
        private boolean fromApache;
        private boolean fromCaddy;
        private boolean fromTraefik;
        private boolean fromCertbot;
        private boolean certPrimary;
        private Instant certExpiry;

        private CandidateSignal(String hostname) {
            this.hostname = hostname;
        }

        private String hostname() {
            return hostname;
        }

        private boolean hasAnySource() {
            return fromNginx || fromApache || fromCaddy || fromTraefik || fromCertbot;
        }

        private boolean hasWebConfig() {
            return fromNginx || fromApache || fromCaddy || fromTraefik;
        }
    }

    private static final class ParsedCertificateBuilder {
        private String name;
        private List<String> domains = List.of();
        private Instant expiresAt;

        private ParsedCertificate build() {
            return new ParsedCertificate(name, domains, expiresAt);
        }
    }
}
