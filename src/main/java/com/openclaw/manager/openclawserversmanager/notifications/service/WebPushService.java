package com.openclaw.manager.openclawserversmanager.notifications.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openclaw.manager.openclawserversmanager.common.exception.ResourceNotFoundException;
import com.openclaw.manager.openclawserversmanager.notifications.entity.NotificationProvider;
import com.openclaw.manager.openclawserversmanager.notifications.entity.NotificationProviderType;
import com.openclaw.manager.openclawserversmanager.notifications.entity.PushSubscription;
import com.openclaw.manager.openclawserversmanager.notifications.mapper.NotificationProviderMapper;
import com.openclaw.manager.openclawserversmanager.notifications.repository.PushSubscriptionRepository;
import com.openclaw.manager.openclawserversmanager.secrets.service.SecretService;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.GeneralSecurityException;
import java.security.Security;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class WebPushService implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(WebPushService.class);
    private final PushSubscriptionRepository subscriptionRepository;
    private final SecretService secretService;
    private final NotificationProviderService providerService;
    private final UserDeviceService userDeviceService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    public WebPushService(PushSubscriptionRepository subscriptionRepository,
                          SecretService secretService,
                          NotificationProviderService providerService,
                          @org.springframework.context.annotation.Lazy UserDeviceService userDeviceService) {
        this.subscriptionRepository = subscriptionRepository;
        this.secretService = secretService;
        this.providerService = providerService;
        this.userDeviceService = userDeviceService;
    }

    @Transactional
    public PushSubscription subscribe(String endpoint, String keyAuth, String keyP256dh, UUID userId) {
        NotificationProvider provider = providerService.getDefaultProvider();

        PushSubscription sub = subscriptionRepository.findByEndpoint(endpoint).orElse(null);
        if (sub != null) {
            sub.setKeyAuth(keyAuth);
            sub.setKeyP256dh(keyP256dh);
            return subscriptionRepository.save(sub);
        }

        sub = new PushSubscription();
        sub.setEndpoint(endpoint);
        sub.setKeyAuth(keyAuth);
        sub.setKeyP256dh(keyP256dh);
        sub.setUserId(userId);
        sub.setProviderId(provider.getId());
        return subscriptionRepository.save(sub);
    }

    @Transactional
    public void unsubscribe(String endpoint) {
        subscriptionRepository.deleteByEndpoint(endpoint);
    }

    @Override
    public NotificationProviderType getProviderType() {
        return NotificationProviderType.WEB_PUSH;
    }

    /**
     * Send a push notification to all subscribers of the default provider.
     * Returns the count of successful deliveries.
     */
    public int sendToAll(String title, String body) {
        NotificationProvider provider = providerService.getDefaultProvider();
        return sendToProvider(provider, title, body);
    }

    /**
     * Send a push notification to all subscribers of a specific provider.
     */
    @Override
    public int sendToProvider(NotificationProvider provider, String title, String body) {
        PushService pushService = buildPushService(provider);
        List<PushSubscription> subs = subscriptionRepository.findByProviderId(provider.getId());

        // Filter out subscriptions linked to devices with notifications disabled
        List<UUID> disabledDeviceIds = userDeviceService.getDisabledDeviceIds();
        if (!disabledDeviceIds.isEmpty()) {
            subs = subs.stream()
                    .filter(s -> s.getDeviceId() == null || !disabledDeviceIds.contains(s.getDeviceId()))
                    .toList();
        }

        int sent = 0;
        for (PushSubscription sub : subs) {
            try {
                String payload = objectMapper.writeValueAsString(Map.of("title", title, "body", body));
                Notification notification = new Notification(
                        sub.getEndpoint(),
                        sub.getKeyP256dh(),
                        sub.getKeyAuth(),
                        payload.getBytes()
                );
                HttpResponse response = pushService.send(notification);
                int statusCode = response.getStatusLine().getStatusCode();

                if (statusCode == 201) {
                    sent++;
                } else if (statusCode == 410 || statusCode == 404) {
                    // Subscription expired or not found — clean up
                    subscriptionRepository.delete(sub);
                    log.info("Removed stale push subscription ({}): {}", statusCode, sub.getEndpoint());
                } else {
                    log.warn("Push service returned {} for {}: {}", statusCode, sub.getEndpoint(),
                            response.getStatusLine().getReasonPhrase());
                }
            } catch (Exception e) {
                log.warn("Failed to send push to {}: {}", sub.getEndpoint(), e.getMessage());
            }
        }

        log.info("Sent push notification to {}/{} subscribers (provider: {})",
                sent, subs.size(), provider.getDisplayName());
        return sent;
    }

    /**
     * Get the VAPID public key for the default provider (needed by clients to subscribe).
     */
    public String getVapidPublicKey() {
        NotificationProvider provider = providerService.getDefaultProvider();
        Map<String, Object> settings = NotificationProviderMapper.deserializeSettings(provider.getProviderSettings());
        if (settings == null || !settings.containsKey("vapidPublicKey")) {
            throw new ResourceNotFoundException("Default provider has no VAPID public key in settings");
        }
        return (String) settings.get("vapidPublicKey");
    }

    private PushService buildPushService(NotificationProvider provider) {
        if (provider.getCredentialId() == null) {
            throw new IllegalStateException("Provider '%s' has no credential configured"
                    .formatted(provider.getDisplayName()));
        }

        String vapidPrivateKey = secretService.decryptSecret(provider.getCredentialId());
        Map<String, Object> settings = NotificationProviderMapper.deserializeSettings(provider.getProviderSettings());
        String vapidPublicKey = settings != null ? (String) settings.get("vapidPublicKey") : null;
        String mailto = settings != null ? (String) settings.getOrDefault("mailto", "mailto:admin@localhost") : "mailto:admin@localhost";

        if (vapidPublicKey == null || vapidPublicKey.isBlank()) {
            throw new IllegalStateException("Provider '%s' missing vapidPublicKey in settings"
                    .formatted(provider.getDisplayName()));
        }

        try {
            PushService pushService = new PushService();
            pushService.setPublicKey(vapidPublicKey);
            pushService.setPrivateKey(vapidPrivateKey);
            pushService.setSubject(mailto);
            return pushService;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to initialize push service: " + e.getMessage(), e);
        }
    }
}
