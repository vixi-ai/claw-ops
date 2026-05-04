package com.openclaw.manager.openclawserversmanager.containerlogs.service;

import com.openclaw.manager.openclawserversmanager.containerlogs.config.ContainerLogsProperties;
import com.openclaw.manager.openclawserversmanager.containerlogs.entity.ContainerService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ContainerLogsTicketService {

    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();
    private final ContainerLogsProperties props;

    public ContainerLogsTicketService(ContainerLogsProperties props) {
        this.props = props;
    }

    public Ticket issue(UUID userId, ContainerService service) {
        String value = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plusSeconds(props.getTicketExpirySeconds());
        Ticket ticket = new Ticket(value, userId, service, expiresAt);
        tickets.put(value, ticket);
        return ticket;
    }

    public Ticket validateAndConsume(String value, ContainerService expectedService) {
        Ticket t = tickets.remove(value);
        if (t == null) return null;
        if (t.expiresAt.isBefore(Instant.now())) return null;
        if (t.service != expectedService) return null;
        return t;
    }

    @Scheduled(fixedRate = 60_000)
    public void purgeExpired() {
        Instant now = Instant.now();
        Iterator<Map.Entry<String, Ticket>> it = tickets.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().expiresAt.isBefore(now)) it.remove();
        }
    }

    public record Ticket(String value, UUID userId, ContainerService service, Instant expiresAt) {
    }
}
