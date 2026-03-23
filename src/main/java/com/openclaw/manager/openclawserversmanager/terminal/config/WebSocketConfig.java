package com.openclaw.manager.openclawserversmanager.terminal.config;

import com.openclaw.manager.openclawserversmanager.common.config.CorsProperties;
import com.openclaw.manager.openclawserversmanager.terminal.handler.TerminalWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final TerminalWebSocketHandler terminalWebSocketHandler;
    private final CorsProperties corsProperties;

    public WebSocketConfig(TerminalWebSocketHandler terminalWebSocketHandler,
                           CorsProperties corsProperties) {
        this.terminalWebSocketHandler = terminalWebSocketHandler;
        this.corsProperties = corsProperties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        String[] origins = corsProperties.getAllowedOriginsArray();
        if (origins.length == 1 && "*".equals(origins[0])) {
            registry.addHandler(terminalWebSocketHandler, "/ws/terminal")
                    .setAllowedOriginPatterns("*");
        } else {
            registry.addHandler(terminalWebSocketHandler, "/ws/terminal")
                    .setAllowedOrigins(origins);
        }
    }
}
