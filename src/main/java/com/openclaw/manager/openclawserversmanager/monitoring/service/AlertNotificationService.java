package com.openclaw.manager.openclawserversmanager.monitoring.service;

import com.openclaw.manager.openclawserversmanager.monitoring.engine.AlertEngine;
import com.openclaw.manager.openclawserversmanager.monitoring.entity.AlertEvent;
import com.openclaw.manager.openclawserversmanager.monitoring.entity.AlertRule;
import com.openclaw.manager.openclawserversmanager.monitoring.entity.NotificationChannel;
import com.openclaw.manager.openclawserversmanager.monitoring.entity.NotificationChannelType;
import com.openclaw.manager.openclawserversmanager.monitoring.repository.AlertRuleRepository;
import com.openclaw.manager.openclawserversmanager.secrets.service.EncryptionService;
import com.openclaw.manager.openclawserversmanager.servers.entity.Server;
import com.openclaw.manager.openclawserversmanager.servers.repository.ServerRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Dispatches alert notifications to configured channels (Slack, Discord, Webhook, Telegram, Email).
 * Listens for AlertFiredEvent and AlertResolvedEvent from AlertEngine.
 */
@Service
public class AlertNotificationService {

    private static final Logger log = LoggerFactory.getLogger(AlertNotificationService.class);

    private final AlertRuleRepository alertRuleRepository;
    private final ServerRepository serverRepository;
    private final EncryptionService encryptionService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient;

    public AlertNotificationService(AlertRuleRepository alertRuleRepository,
                                    ServerRepository serverRepository,
                                    EncryptionService encryptionService) {
        this.alertRuleRepository = alertRuleRepository;
        this.serverRepository = serverRepository;
        this.encryptionService = encryptionService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Async
    @EventListener
    public void onAlertFired(AlertEngine.AlertFiredEvent event) {
        dispatch(event.alertEvent(), event.alertRule(), "FIRING");
    }

    @Async
    @EventListener
    public void onAlertResolved(AlertEngine.AlertResolvedEvent event) {
        dispatch(event.alertEvent(), event.alertRule(), "RESOLVED");
    }

    private void dispatch(AlertEvent alertEvent, AlertRule rule, String state) {
        // Reload rule to get notification channels (lazy-loaded)
        AlertRule fullRule = alertRuleRepository.findById(rule.getId()).orElse(null);
        if (fullRule == null || fullRule.getNotificationChannels() == null) return;

        List<NotificationChannel> channels = fullRule.getNotificationChannels();
        if (channels.isEmpty()) {
            log.debug("No notification channels configured for rule: {}", rule.getName());
            return;
        }

        String serverName = serverRepository.findById(alertEvent.getServerId())
                .map(Server::getName)
                .orElse(alertEvent.getServerId().toString());

        for (NotificationChannel channel : channels) {
            if (!channel.isEnabled()) continue;
            try {
                String configJson = encryptionService.decrypt(channel.getConfig(), channel.getConfigIv());
                JsonNode config = objectMapper.readTree(configJson);

                switch (channel.getChannelType()) {
                    case WEBHOOK -> sendWebhook(config, alertEvent, rule, serverName, state);
                    case SLACK -> sendSlack(config, alertEvent, rule, serverName, state);
                    case DISCORD -> sendDiscord(config, alertEvent, rule, serverName, state);
                    case TELEGRAM -> sendTelegram(config, alertEvent, rule, serverName, state);
                    case EMAIL -> log.warn("Email dispatch not yet implemented for channel: {}", channel.getName());
                }

                log.info("Notification sent via {} channel '{}' for rule '{}'",
                        channel.getChannelType(), channel.getName(), rule.getName());
            } catch (Exception e) {
                log.error("Failed to dispatch notification via channel '{}': {}", channel.getName(), e.getMessage());
            }
        }
    }

    private void sendWebhook(JsonNode config, AlertEvent event, AlertRule rule,
                             String serverName, String state) throws Exception {
        String url = config.path("url").asText();
        if (url.isBlank()) throw new IllegalArgumentException("Webhook URL is empty");

        Map<String, Object> payload = Map.of(
                "state", state,
                "rule", rule.getName(),
                "severity", event.getSeverity().name(),
                "server", serverName,
                "serverId", event.getServerId().toString(),
                "metricType", event.getMetricType() != null ? event.getMetricType().name() : "",
                "metricValue", event.getMetricValue() != null ? event.getMetricValue() : 0,
                "message", event.getMessage() != null ? event.getMessage() : "",
                "firedAt", event.getFiredAt().toString()
        );

        String body = objectMapper.writeValueAsString(payload);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(15))
                .build();

        httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private void sendSlack(JsonNode config, AlertEvent event, AlertRule rule,
                           String serverName, String state) throws Exception {
        String webhookUrl = config.path("webhookUrl").asText();
        if (webhookUrl.isBlank()) throw new IllegalArgumentException("Slack webhook URL is empty");

        String emoji = "FIRING".equals(state) ? ":rotating_light:" : ":white_check_mark:";
        String color = "FIRING".equals(state) ? "#dc3545" : "#28a745";
        String valueStr = event.getMetricValue() != null ? String.format("%.2f", event.getMetricValue()) : "N/A";

        String text = String.format("%s *%s* | %s\n*Server:* %s | *Metric:* %s = %s\n%s",
                emoji, state, rule.getName(), serverName,
                event.getMetricType(), valueStr,
                event.getMessage() != null ? event.getMessage() : "");

        Map<String, Object> payload = Map.of(
                "attachments", List.of(Map.of(
                        "color", color,
                        "text", text,
                        "fallback", rule.getName() + " - " + state
                ))
        );

        postJson(webhookUrl, objectMapper.writeValueAsString(payload));
    }

    private void sendDiscord(JsonNode config, AlertEvent event, AlertRule rule,
                             String serverName, String state) throws Exception {
        String webhookUrl = config.path("webhookUrl").asText();
        if (webhookUrl.isBlank()) throw new IllegalArgumentException("Discord webhook URL is empty");

        int color = "FIRING".equals(state) ? 0xDC3545 : 0x28A745;
        String valueStr = event.getMetricValue() != null ? String.format("%.2f", event.getMetricValue()) : "N/A";

        Map<String, Object> embed = Map.of(
                "title", state + ": " + rule.getName(),
                "description", event.getMessage() != null ? event.getMessage() : "",
                "color", color,
                "fields", List.of(
                        Map.of("name", "Server", "value", serverName, "inline", true),
                        Map.of("name", "Severity", "value", event.getSeverity().name(), "inline", true),
                        Map.of("name", "Metric", "value", event.getMetricType() + " = " + valueStr, "inline", true)
                )
        );

        Map<String, Object> payload = Map.of(
                "embeds", List.of(embed)
        );

        postJson(webhookUrl, objectMapper.writeValueAsString(payload));
    }

    private void sendTelegram(JsonNode config, AlertEvent event, AlertRule rule,
                              String serverName, String state) throws Exception {
        String botToken = config.path("botToken").asText();
        String chatId = config.path("chatId").asText();
        if (botToken.isBlank() || chatId.isBlank())
            throw new IllegalArgumentException("Telegram botToken or chatId is empty");

        String emoji = "FIRING".equals(state) ? "\uD83D\uDEA8" : "\u2705";
        String valueStr = event.getMetricValue() != null ? String.format("%.2f", event.getMetricValue()) : "N/A";
        String text = String.format("%s <b>%s</b>: %s\n<b>Server:</b> %s\n<b>Metric:</b> %s = %s\n%s",
                emoji, state, rule.getName(), serverName,
                event.getMetricType(), valueStr,
                event.getMessage() != null ? event.getMessage() : "");

        String url = String.format("https://api.telegram.org/bot%s/sendMessage", botToken);
        Map<String, Object> payload = Map.of(
                "chat_id", chatId,
                "text", text,
                "parse_mode", "HTML"
        );

        postJson(url, objectMapper.writeValueAsString(payload));
    }

    private void postJson(String url, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(15))
                .build();
        httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
