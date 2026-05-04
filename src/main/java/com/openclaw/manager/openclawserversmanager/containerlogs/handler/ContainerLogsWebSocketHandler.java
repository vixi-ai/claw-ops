package com.openclaw.manager.openclawserversmanager.containerlogs.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openclaw.manager.openclawserversmanager.containerlogs.config.ContainerLogsProperties;
import com.openclaw.manager.openclawserversmanager.containerlogs.dto.LiveTailMessage;
import com.openclaw.manager.openclawserversmanager.containerlogs.entity.ContainerLog;
import com.openclaw.manager.openclawserversmanager.containerlogs.entity.ContainerService;
import com.openclaw.manager.openclawserversmanager.containerlogs.repository.ContainerLogRepository;
import com.openclaw.manager.openclawserversmanager.containerlogs.service.ContainerLogFanout;
import com.openclaw.manager.openclawserversmanager.containerlogs.service.ContainerLogsTicketService;
import com.openclaw.manager.openclawserversmanager.containerlogs.service.ContainerLogsTicketService.Ticket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ContainerLogsWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ContainerLogsWebSocketHandler.class);

    private final ContainerLogsTicketService ticketService;
    private final ContainerLogFanout fanout;
    private final ContainerLogRepository repository;
    private final ContainerLogsProperties props;
    private final ObjectMapper objectMapper;

    public ContainerLogsWebSocketHandler(ContainerLogsTicketService ticketService,
                                         ContainerLogFanout fanout,
                                         ContainerLogRepository repository,
                                         ContainerLogsProperties props,
                                         ObjectMapper objectMapper) {
        this.ticketService = ticketService;
        this.fanout = fanout;
        this.repository = repository;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Map<String, String> q = parseQuery(session.getUri());
        String token = q.get("token");
        String serviceParam = q.get("service");

        ContainerService service;
        try {
            service = ContainerService.valueOf(serviceParam);
        } catch (Exception e) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Invalid service"));
            return;
        }

        Ticket ticket = token == null ? null : ticketService.validateAndConsume(token, service);
        if (ticket == null) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Invalid or expired ticket"));
            return;
        }

        session.getAttributes().put("service", service);
        session.getAttributes().put("userId", ticket.userId());

        if (!fanout.register(service, session)) {
            fanout.sendWarning(session, "Subscriber limit reached for " + service);
            session.close(CloseStatus.SERVICE_OVERLOAD);
            return;
        }

        log.info("Container logs WS opened: session={} service={} user={}", session.getId(), service, ticket.userId());

        replayBacklog(session, service);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        if (payload.length() > 1024) {
            // ignore oversized client messages
            return;
        }
        try {
            JsonNode node = objectMapper.readTree(payload);
            String type = node.path("type").asText("");
            if ("PING".equals(type)) {
                fanout.sendPong(session);
            }
        } catch (Exception ignored) {
            // ignore malformed client frames
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        ContainerService service = (ContainerService) session.getAttributes().get("service");
        if (service != null) {
            fanout.unregister(service, session);
            log.info("Container logs WS closed: session={} service={} status={}", session.getId(), service, status);
        }
    }

    private void replayBacklog(WebSocketSession session, ContainerService service) {
        int n = props.getReplayOnConnect();
        if (n <= 0) return;
        try {
            List<ContainerLog> recent = repository.findByServiceOrderByLogTsDesc(service, PageRequest.of(0, n));
            // Send oldest-first.
            for (int i = recent.size() - 1; i >= 0; i--) {
                ContainerLog row = recent.get(i);
                LiveTailMessage frame = LiveTailMessage.log(
                        row.getId(), row.getService(), row.getContainerName(),
                        row.getStream(), row.getLevel(), row.getMessage(), row.getLogTs());
                String json = objectMapper.writeValueAsString(frame);
                synchronized (session) {
                    if (!session.isOpen()) return;
                    session.sendMessage(new TextMessage(json));
                }
            }
        } catch (Exception e) {
            log.warn("Replay backlog failed for service={}: {}", service, e.getMessage());
        }
    }

    private Map<String, String> parseQuery(URI uri) {
        Map<String, String> map = new HashMap<>();
        if (uri == null || uri.getQuery() == null) return map;
        for (String pair : uri.getQuery().split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0) continue;
            map.put(pair.substring(0, eq), java.net.URLDecoder.decode(pair.substring(eq + 1), java.nio.charset.StandardCharsets.UTF_8));
        }
        return map;
    }
}
