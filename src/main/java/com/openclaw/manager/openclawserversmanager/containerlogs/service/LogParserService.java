package com.openclaw.manager.openclawserversmanager.containerlogs.service;

import com.openclaw.manager.openclawserversmanager.containerlogs.config.ContainerLogsProperties;
import com.openclaw.manager.openclawserversmanager.containerlogs.entity.ContainerLogLevel;
import com.openclaw.manager.openclawserversmanager.containerlogs.entity.ContainerService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class LogParserService {

    /** Strips ANSI CSI / OSC sequences. */
    private static final Pattern ANSI = Pattern.compile("\\[[0-?]*[ -/]*[@-~]");

    /** Spring Boot default pattern: 2026-05-03T10:15:30.123Z  INFO 1 --- ... */
    private static final Pattern SPRING_LEVEL = Pattern.compile(
            "^\\s*(?:\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(?:[.,]\\d+)?Z?\\s+)?(TRACE|DEBUG|INFO|WARN|WARNING|ERROR|FATAL)\\b"
    );

    private static final Pattern NGINX_LEVEL = Pattern.compile("\\[(emerg|alert|crit|error|warn|notice|info|debug)]");
    private static final Pattern POSTGRES_LEVEL = Pattern.compile("\\b(LOG|WARNING|ERROR|FATAL|PANIC|DEBUG\\d?|NOTICE|INFO)\\s*:");

    private final ContainerLogsProperties props;

    public LogParserService(ContainerLogsProperties props) {
        this.props = props;
    }

    public ParsedLine parse(ContainerService service, String rawLine) {
        if (rawLine == null) {
            return new ParsedLine(Instant.now(), ContainerLogLevel.UNKNOWN, "");
        }

        String working = rawLine;
        Instant ts = Instant.now();

        // Docker --timestamps prefix: "2026-05-03T10:15:30.123456789Z <message>"
        int firstSpace = working.indexOf(' ');
        if (firstSpace > 0 && firstSpace < 35) {
            String maybeTs = working.substring(0, firstSpace);
            try {
                ts = Instant.parse(maybeTs);
                working = working.substring(firstSpace + 1);
            } catch (DateTimeParseException ignored) {
                // not a docker timestamp, leave the line as-is
            }
        }

        String sanitized = sanitize(working);
        ContainerLogLevel level = detectLevel(service, sanitized);

        int max = props.getMaxLineLength();
        if (sanitized.length() > max) {
            sanitized = sanitized.substring(0, max);
        }

        return new ParsedLine(ts, level, sanitized);
    }

    String sanitize(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        String stripped = ANSI.matcher(raw).replaceAll("");
        StringBuilder sb = new StringBuilder(stripped.length());
        for (int i = 0; i < stripped.length(); i++) {
            char c = stripped.charAt(i);
            if (c == '\t') {
                sb.append(c);
            } else if (c == '\n' || c == '\r') {
                // Lines have already been split before this; reinjected newlines collapse to space.
                sb.append(' ');
            } else if (c < 0x20 || c == 0x7F) {
                sb.append('�');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private ContainerLogLevel detectLevel(ContainerService service, String line) {
        return switch (service) {
            case BACKEND -> springLevel(line);
            case FRONTEND -> frontendLevel(line);
            case NGINX -> nginxLevel(line);
            case POSTGRES -> postgresLevel(line);
        };
    }

    private ContainerLogLevel springLevel(String line) {
        Matcher m = SPRING_LEVEL.matcher(line);
        if (!m.find()) return ContainerLogLevel.UNKNOWN;
        return mapWord(m.group(1));
    }

    private ContainerLogLevel nginxLevel(String line) {
        Matcher m = NGINX_LEVEL.matcher(line);
        if (!m.find()) return ContainerLogLevel.INFO;
        return switch (m.group(1).toLowerCase()) {
            case "emerg", "alert", "crit", "error" -> ContainerLogLevel.ERROR;
            case "warn" -> ContainerLogLevel.WARN;
            case "debug" -> ContainerLogLevel.DEBUG;
            default -> ContainerLogLevel.INFO;
        };
    }

    private ContainerLogLevel postgresLevel(String line) {
        Matcher m = POSTGRES_LEVEL.matcher(line);
        if (!m.find()) return ContainerLogLevel.UNKNOWN;
        return mapWord(m.group(1));
    }

    private ContainerLogLevel frontendLevel(String line) {
        String trimmed = line.stripLeading();
        if (trimmed.isEmpty()) return ContainerLogLevel.UNKNOWN;
        if (trimmed.regionMatches(true, 0, "error", 0, 5) || trimmed.contains("Error:") || trimmed.contains("ERR_")) {
            return ContainerLogLevel.ERROR;
        }
        if (trimmed.regionMatches(true, 0, "warn", 0, 4)) {
            return ContainerLogLevel.WARN;
        }
        if (trimmed.regionMatches(true, 0, "debug", 0, 5)) {
            return ContainerLogLevel.DEBUG;
        }
        return ContainerLogLevel.INFO;
    }

    private ContainerLogLevel mapWord(String word) {
        return switch (word.toUpperCase()) {
            case "TRACE", "DEBUG" -> ContainerLogLevel.DEBUG;
            case "INFO", "LOG", "NOTICE" -> ContainerLogLevel.INFO;
            case "WARN", "WARNING" -> ContainerLogLevel.WARN;
            case "ERROR", "FATAL", "PANIC" -> ContainerLogLevel.ERROR;
            default -> ContainerLogLevel.UNKNOWN;
        };
    }

    public record ParsedLine(Instant ts, ContainerLogLevel level, String message) {
    }
}
