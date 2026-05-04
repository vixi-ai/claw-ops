package com.openclaw.manager.openclawserversmanager.containerlogs.config;

import com.openclaw.manager.openclawserversmanager.common.config.CorsProperties;
import com.openclaw.manager.openclawserversmanager.containerlogs.handler.ContainerLogsWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class ContainerLogsWebSocketRegistration implements WebSocketConfigurer {

    private final ContainerLogsWebSocketHandler handler;
    private final CorsProperties corsProperties;

    public ContainerLogsWebSocketRegistration(ContainerLogsWebSocketHandler handler,
                                              CorsProperties corsProperties) {
        this.handler = handler;
        this.corsProperties = corsProperties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        String[] origins = corsProperties.getAllowedOriginsArray();
        if (origins.length == 1 && "*".equals(origins[0])) {
            registry.addHandler(handler, "/ws/container-logs")
                    .setAllowedOriginPatterns("*");
        } else {
            registry.addHandler(handler, "/ws/container-logs")
                    .setAllowedOrigins(origins);
        }
    }
}
