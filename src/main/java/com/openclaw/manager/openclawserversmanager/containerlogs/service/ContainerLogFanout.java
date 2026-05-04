package com.openclaw.manager.openclawserversmanager.containerlogs.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openclaw.manager.openclawserversmanager.containerlogs.config.ContainerLogsProperties;
import com.openclaw.manager.openclawserversmanager.containerlogs.dto.LiveTailMessage;
import com.openclaw.manager.openclawserversmanager.containerlogs.entity.ContainerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class ContainerLogFanout {

    private static final Logger log = LoggerFactory.getLogger(ContainerLogFanout.class);

    private final Map<ContainerService, CopyOnWriteArrayList<WebSocketSession>> subscribers = new EnumMap<>(ContainerService.class);
    private final Map<String, AtomicInteger> outboxSize = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final ContainerLogsProperties props;

    public ContainerLogFanout(ObjectMapper objectMapper, ContainerLogsProperties props) {
        this.objectMapper = objectMapper;
        this.props = props;
        for (ContainerService s : ContainerService.values()) {
            subscribers.put(s, new CopyOnWriteArrayList<>());
        }
    }

    /**
     * Register a session as a subscriber. Returns false if the per-service cap is exceeded.
     */
    public boolean register(ContainerService service, WebSocketSession session) {
        CopyOnWriteArrayList<WebSocketSession> list = subscribers.get(service);
        if (list.size() >= props.getWs().getMaxSubscribersPerService()) {
            return false;
        }
        list.add(session);
        outboxSize.put(session.getId(), new AtomicInteger(0));
        return true;
    }

    public void unregister(ContainerService service, WebSocketSession session) {
        subscribers.get(service).remove(session);
        outboxSize.remove(session.getId());
    }

    public int subscriberCount(ContainerService service) {
        return subscribers.get(service).size();
    }

    public void broadcast(ContainerService service, ContainerLogEvent evt, Long persistedId) {
        CopyOnWriteArrayList<WebSocketSession> list = subscribers.get(service);
        if (list.isEmpty()) return;
        LiveTailMessage frame = LiveTailMessage.log(persistedId, service, evt.containerName(),
                evt.stream(), evt.level(), evt.message(), evt.logTs());
        String payload;
        try {
            payload = objectMapper.writeValueAsString(frame);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize log frame for service={}", service, e);
            return;
        }
        TextMessage msg = new TextMessage(payload);
        int outboxCap = props.getWs().getSlowConsumerBuffer();
        for (WebSocketSession session : list) {
            if (!session.isOpen()) continue;
            AtomicInteger size = outboxSize.get(session.getId());
            if (size == null) continue;
            if (size.get() >= outboxCap) {
                // slow consumer; drop
                continue;
            }
            try {
                synchronized (session) {
                    if (session.isOpen()) {
                        session.sendMessage(msg);
                    }
                }
            } catch (IOException | IllegalStateException e) {
                log.debug("Drop subscriber {} for {} ({})", session.getId(), service, e.getMessage());
                size.incrementAndGet();
            }
        }
    }

    public void sendWarning(WebSocketSession session, String text) {
        try {
            String payload = objectMapper.writeValueAsString(LiveTailMessage.warning(text));
            synchronized (session) {
                if (session.isOpen()) session.sendMessage(new TextMessage(payload));
            }
        } catch (IOException e) {
            log.debug("Failed sending WARNING to {}: {}", session.getId(), e.getMessage());
        }
    }

    public void sendPong(WebSocketSession session) {
        try {
            String payload = objectMapper.writeValueAsString(LiveTailMessage.pong());
            synchronized (session) {
                if (session.isOpen()) session.sendMessage(new TextMessage(payload));
            }
        } catch (IOException e) {
            log.debug("Failed sending PONG to {}: {}", session.getId(), e.getMessage());
        }
    }
}
