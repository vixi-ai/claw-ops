package com.openclaw.manager.openclawserversmanager.monitoring.collector;

import com.openclaw.manager.openclawserversmanager.monitoring.entity.MetricType;
import com.openclaw.manager.openclawserversmanager.servers.entity.Server;
import com.openclaw.manager.openclawserversmanager.ssh.service.SshService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class SshMetricCollector implements MetricCollector {

    private static final Logger log = LoggerFactory.getLogger(SshMetricCollector.class);

    private static final long SSH_TIMEOUT_SECONDS = 30;

    private static final String METRICS_COMMAND = String.join("\n",
            "echo '---CPU---'",
            "top -bn1 | head -5",
            "echo '---MEM---'",
            "free -b",
            "echo '---DISK---'",
            "df -B1 --output=target,size,used,avail,pcent 2>/dev/null || df -k",
            "echo '---LOAD---'",
            "cat /proc/loadavg",
            "echo '---UPTIME---'",
            "cat /proc/uptime",
            "echo '---PROCS---'",
            "ps aux --no-headers 2>/dev/null | wc -l",
            "echo '---SWAP---'",
            "free -b | grep -i swap",
            "echo '---NET---'",
            "cat /proc/net/dev | tail -n +3",
            "echo '---END---'"
    );

    private final SshService sshService;

    public SshMetricCollector(SshService sshService) {
        this.sshService = sshService;
    }

    @Override
    public CollectionResult collect(Server server) {
        Instant collectedAt = Instant.now();
        Map<String, Double> metrics = new HashMap<>();
        List<String> errors = new ArrayList<>();

        try {
            var result = sshService.executeCommand(server, METRICS_COMMAND, SSH_TIMEOUT_SECONDS);
            long durationMs = result.durationMs();

            if (result.exitCode() != 0 && result.stdout().isEmpty()) {
                log.warn("SSH command failed for server {} (exit={}): {}",
                        server.getName(), result.exitCode(), result.stderr());
                return new CollectionResult(server.getId(), metrics, collectedAt, durationMs, true,
                        List.of("Command failed with exit code " + result.exitCode() + ": " + result.stderr()));
            }

            Map<String, String> sections = parseSections(result.stdout());

            parseCpuSection(sections.get("CPU"), metrics, errors);
            parseMemorySection(sections.get("MEM"), metrics, errors);
            parseDiskSection(sections.get("DISK"), metrics, errors);
            parseLoadSection(sections.get("LOAD"), metrics, errors);
            parseUptimeSection(sections.get("UPTIME"), metrics, errors);
            parseProcessCountSection(sections.get("PROCS"), metrics, errors);
            parseSwapSection(sections.get("SWAP"), metrics, errors);
            parseNetworkSection(sections.get("NET"), metrics, errors);

            if (!errors.isEmpty()) {
                log.warn("Partial collection failures for server {}: {}", server.getName(), errors);
            }

            return new CollectionResult(server.getId(), metrics, collectedAt, durationMs, true, errors);

        } catch (Exception e) {
            log.error("SSH connection failed for server {}: {}", server.getName(), e.getMessage());
            return new CollectionResult(server.getId(), metrics, collectedAt, 0, false,
                    List.of("SSH connection failed: " + e.getMessage()));
        }
    }

    @Override
    public String getCollectorType() {
        return "SSH";
    }

    private Map<String, String> parseSections(String output) {
        Map<String, String> sections = new HashMap<>();
        String[] markers = {"CPU", "MEM", "DISK", "LOAD", "UPTIME", "PROCS", "SWAP", "NET", "END"};

        for (int i = 0; i < markers.length - 1; i++) {
            String startMarker = "---" + markers[i] + "---";
            String endMarker = "---" + markers[i + 1] + "---";

            int startIdx = output.indexOf(startMarker);
            int endIdx = output.indexOf(endMarker);

            if (startIdx >= 0 && endIdx > startIdx) {
                String content = output.substring(startIdx + startMarker.length(), endIdx).trim();
                sections.put(markers[i], content);
            }
        }

        return sections;
    }

    private void parseCpuSection(String section, Map<String, Double> metrics, List<String> errors) {
        if (section == null || section.isBlank()) {
            errors.add("CPU section missing or empty");
            return;
        }

        try {
            // Look for "%Cpu(s):" line from top output
            // Format: %Cpu(s):  2.0 us,  1.0 sy,  0.0 ni, 96.5 id, ...
            for (String line : section.split("\n")) {
                String trimmed = line.trim();
                if (trimmed.contains("Cpu") && trimmed.contains("id")) {
                    double idle = extractCpuIdle(trimmed);
                    double usage = 100.0 - idle;
                    metrics.put(MetricType.CPU_USAGE_PERCENT.name(), Math.max(0, Math.min(100, usage)));
                    return;
                }
            }
            errors.add("CPU: could not find Cpu idle line in top output");
        } catch (Exception e) {
            errors.add("CPU parse error: " + e.getMessage());
        }
    }

    private double extractCpuIdle(String cpuLine) {
        // Handle both formats:
        // "%Cpu(s):  2.0 us,  1.0 sy,  0.0 ni, 96.5 id, ..."
        // "%Cpu(s):  2.0%us,  1.0%sy,  0.0%ni, 96.5%id, ..."
        String[] parts = cpuLine.split(",");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.contains("id")) {
                String numStr = trimmed.replaceAll("[^0-9.]", "");
                if (!numStr.isEmpty()) {
                    return Double.parseDouble(numStr);
                }
            }
        }
        throw new IllegalArgumentException("No idle value found in: " + cpuLine);
    }

    private void parseMemorySection(String section, Map<String, Double> metrics, List<String> errors) {
        if (section == null || section.isBlank()) {
            errors.add("Memory section missing or empty");
            return;
        }

        try {
            // Parse "free -b" output
            // Mem:  total  used  free  shared  buff/cache  available
            for (String line : section.split("\n")) {
                String trimmed = line.trim();
                if (trimmed.startsWith("Mem:")) {
                    String[] parts = trimmed.split("\\s+");
                    if (parts.length >= 3) {
                        double total = Double.parseDouble(parts[1]);
                        double used = Double.parseDouble(parts[2]);
                        metrics.put(MetricType.MEMORY_TOTAL_BYTES.name(), total);
                        metrics.put(MetricType.MEMORY_USED_BYTES.name(), used);
                        if (total > 0) {
                            metrics.put(MetricType.MEMORY_USAGE_PERCENT.name(),
                                    Math.round(used / total * 10000.0) / 100.0);
                        }
                        return;
                    }
                }
            }
            errors.add("Memory: could not find Mem line in free output");
        } catch (Exception e) {
            errors.add("Memory parse error: " + e.getMessage());
        }
    }

    private void parseDiskSection(String section, Map<String, Double> metrics, List<String> errors) {
        if (section == null || section.isBlank()) {
            errors.add("Disk section missing or empty");
            return;
        }

        try {
            // Parse "df -B1 --output=target,size,used,avail,pcent" output
            // Or fallback "df -k" output
            String[] lines = section.split("\n");
            boolean isFirstDf = section.contains("Mounted on") || section.contains("target");
            double maxDiskUsage = 0;

            for (String line : lines) {
                String trimmed = line.trim();
                // Skip header lines
                if (trimmed.isEmpty() || trimmed.startsWith("Filesystem") || trimmed.startsWith("Mounted")) {
                    continue;
                }

                // Skip non-physical filesystems
                if (trimmed.contains("tmpfs") || trimmed.contains("devtmpfs") ||
                    trimmed.contains("udev") || trimmed.contains("overlay")) {
                    continue;
                }

                String[] parts = trimmed.split("\\s+");
                if (isFirstDf && parts.length >= 5) {
                    // --output=target,size,used,avail,pcent
                    String mount = parts[0];
                    double size = parseNumeric(parts[1]);
                    double used = parseNumeric(parts[2]);
                    String pcentStr = parts[4].replace("%", "");
                    double pcent = Double.parseDouble(pcentStr);

                    String label = mount.equals("/") ? "root" : mount.replaceFirst("^/", "");
                    metrics.put(MetricType.DISK_TOTAL_BYTES.name() + ":" + label, size);
                    metrics.put(MetricType.DISK_USED_BYTES.name() + ":" + label, used);
                    metrics.put(MetricType.DISK_USAGE_PERCENT.name() + ":" + label, pcent);

                    maxDiskUsage = Math.max(maxDiskUsage, pcent);
                } else if (!isFirstDf && parts.length >= 6) {
                    // Standard df -k: Filesystem 1K-blocks Used Available Use% Mounted
                    String mount = parts[5];
                    double sizeKb = parseNumeric(parts[1]);
                    double usedKb = parseNumeric(parts[2]);
                    String pcentStr = parts[4].replace("%", "");
                    double pcent = Double.parseDouble(pcentStr);

                    String label = mount.equals("/") ? "root" : mount.replaceFirst("^/", "");
                    metrics.put(MetricType.DISK_TOTAL_BYTES.name() + ":" + label, sizeKb * 1024);
                    metrics.put(MetricType.DISK_USED_BYTES.name() + ":" + label, usedKb * 1024);
                    metrics.put(MetricType.DISK_USAGE_PERCENT.name() + ":" + label, pcent);

                    maxDiskUsage = Math.max(maxDiskUsage, pcent);
                }
            }

            // Store the highest disk usage as the overall disk metric
            if (maxDiskUsage > 0) {
                metrics.put(MetricType.DISK_USAGE_PERCENT.name(), maxDiskUsage);
            }
        } catch (Exception e) {
            errors.add("Disk parse error: " + e.getMessage());
        }
    }

    private void parseLoadSection(String section, Map<String, Double> metrics, List<String> errors) {
        if (section == null || section.isBlank()) {
            errors.add("Load section missing or empty");
            return;
        }

        try {
            // /proc/loadavg format: "0.12 0.15 0.10 1/230 12345"
            String[] parts = section.trim().split("\\s+");
            if (parts.length >= 3) {
                metrics.put(MetricType.LOAD_1M.name(), Double.parseDouble(parts[0]));
                metrics.put(MetricType.LOAD_5M.name(), Double.parseDouble(parts[1]));
                metrics.put(MetricType.LOAD_15M.name(), Double.parseDouble(parts[2]));
            } else {
                errors.add("Load: unexpected format: " + section.trim());
            }
        } catch (Exception e) {
            errors.add("Load parse error: " + e.getMessage());
        }
    }

    private void parseUptimeSection(String section, Map<String, Double> metrics, List<String> errors) {
        if (section == null || section.isBlank()) {
            errors.add("Uptime section missing or empty");
            return;
        }

        try {
            // /proc/uptime format: "12345.67 23456.78"
            String[] parts = section.trim().split("\\s+");
            if (parts.length >= 1) {
                metrics.put(MetricType.UPTIME_SECONDS.name(), Double.parseDouble(parts[0]));
            }
        } catch (Exception e) {
            errors.add("Uptime parse error: " + e.getMessage());
        }
    }

    private void parseProcessCountSection(String section, Map<String, Double> metrics, List<String> errors) {
        if (section == null || section.isBlank()) {
            errors.add("Process count section missing or empty");
            return;
        }

        try {
            int count = Integer.parseInt(section.trim());
            metrics.put(MetricType.PROCESS_COUNT.name(), (double) count);
        } catch (Exception e) {
            errors.add("Process count parse error: " + e.getMessage());
        }
    }

    private void parseSwapSection(String section, Map<String, Double> metrics, List<String> errors) {
        if (section == null || section.isBlank()) {
            // Swap might not exist on all systems — not an error
            metrics.put(MetricType.SWAP_USAGE_PERCENT.name(), 0.0);
            return;
        }

        try {
            // "Swap:  total  used  free"
            String[] parts = section.trim().split("\\s+");
            if (parts.length >= 3) {
                double total = parseNumeric(parts[1]);
                double used = parseNumeric(parts[2]);
                if (total > 0) {
                    metrics.put(MetricType.SWAP_USAGE_PERCENT.name(),
                            Math.round(used / total * 10000.0) / 100.0);
                } else {
                    metrics.put(MetricType.SWAP_USAGE_PERCENT.name(), 0.0);
                }
            }
        } catch (Exception e) {
            errors.add("Swap parse error: " + e.getMessage());
        }
    }

    private void parseNetworkSection(String section, Map<String, Double> metrics, List<String> errors) {
        if (section == null || section.isBlank()) {
            errors.add("Network section missing or empty");
            return;
        }

        try {
            // /proc/net/dev format (after skipping 2 header lines):
            // "  eth0: rx_bytes rx_packets ... tx_bytes tx_packets ..."
            for (String line : section.split("\n")) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;

                String[] colonSplit = trimmed.split(":");
                if (colonSplit.length != 2) continue;

                String iface = colonSplit[0].trim();
                // Skip loopback
                if ("lo".equals(iface)) continue;

                String[] values = colonSplit[1].trim().split("\\s+");
                if (values.length >= 9) {
                    double rxBytes = Double.parseDouble(values[0]);
                    double txBytes = Double.parseDouble(values[8]);
                    metrics.put(MetricType.NETWORK_RX_BYTES.name() + ":" + iface, rxBytes);
                    metrics.put(MetricType.NETWORK_TX_BYTES.name() + ":" + iface, txBytes);
                }
            }
        } catch (Exception e) {
            errors.add("Network parse error: " + e.getMessage());
        }
    }

    private double parseNumeric(String value) {
        return Double.parseDouble(value.replaceAll("[^0-9.]", ""));
    }
}
