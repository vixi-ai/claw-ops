package com.openclaw.manager.openclawserversmanager.notifications.service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import com.google.firebase.messaging.WebpushConfig;
import com.google.firebase.messaging.WebpushNotification;
import com.openclaw.manager.openclawserversmanager.notifications.entity.DeviceToken;
import com.openclaw.manager.openclawserversmanager.notifications.entity.NotificationProvider;
import com.openclaw.manager.openclawserversmanager.notifications.entity.NotificationProviderType;
import com.openclaw.manager.openclawserversmanager.notifications.repository.DeviceTokenRepository;
import com.openclaw.manager.openclawserversmanager.secrets.service.SecretService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FirebaseService implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(FirebaseService.class);
    private static final int FCM_BATCH_SIZE = 500;

    private final DeviceTokenRepository tokenRepository;
    private final SecretService secretService;
    private final NotificationProviderService providerService;
    private final UserDeviceService userDeviceService;
    private final ConcurrentHashMap<UUID, FirebaseApp> appCache = new ConcurrentHashMap<>();

    public FirebaseService(DeviceTokenRepository tokenRepository,
                           SecretService secretService,
                           NotificationProviderService providerService,
                           @org.springframework.context.annotation.Lazy UserDeviceService userDeviceService) {
        this.tokenRepository = tokenRepository;
        this.secretService = secretService;
        this.providerService = providerService;
        this.userDeviceService = userDeviceService;
    }

    @Override
    public NotificationProviderType getProviderType() {
        return NotificationProviderType.FCM;
    }

    @Transactional
    public DeviceToken subscribe(String token, String platform, UUID userId) {
        NotificationProvider provider = providerService.getDefaultProvider();
        return subscribeToProvider(token, platform, userId, provider);
    }

    @Transactional
    public DeviceToken subscribeToProvider(String token, String platform, UUID userId,
                                           NotificationProvider provider) {
        DeviceToken existing = tokenRepository.findByTokenAndProviderId(token, provider.getId())
                .orElse(null);
        if (existing != null) {
            existing.setUserId(userId);
            return tokenRepository.save(existing);
        }

        DeviceToken dt = new DeviceToken();
        dt.setToken(token);
        dt.setPlatform(platform);
        dt.setUserId(userId);
        dt.setProviderId(provider.getId());
        return tokenRepository.save(dt);
    }

    @Transactional
    public void unsubscribe(String token) {
        NotificationProvider provider = providerService.getDefaultProvider();
        tokenRepository.deleteByTokenAndProviderId(token, provider.getId());
    }

    @Override
    public int sendToProvider(NotificationProvider provider, String title, String body) {
        FirebaseApp app = getOrCreateApp(provider);
        FirebaseMessaging messaging = FirebaseMessaging.getInstance(app);
        List<DeviceToken> tokens = tokenRepository.findByProviderId(provider.getId());

        // Filter out tokens linked to devices with notifications disabled
        List<UUID> disabledDeviceIds = userDeviceService.getDisabledDeviceIds();
        if (!disabledDeviceIds.isEmpty()) {
            tokens = tokens.stream()
                    .filter(t -> t.getDeviceId() == null || !disabledDeviceIds.contains(t.getDeviceId()))
                    .toList();
        }

        if (tokens.isEmpty()) return 0;

        Notification notification = Notification.builder()
                .setTitle(title)
                .setBody(body)
                .setImage("/dev/logo.png")
                .build();

        WebpushConfig webpushConfig = WebpushConfig.builder()
                .setNotification(WebpushNotification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .setIcon("/dev/logo.png")
                        .setBadge("/dev/logo.png")
                        .setTag("clawops")
                        .setRenotify(true)
                        .build())
                .build();

        List<String> tokenStrings = tokens.stream().map(DeviceToken::getToken).toList();
        int sent = 0;

        for (int i = 0; i < tokenStrings.size(); i += FCM_BATCH_SIZE) {
            List<String> batch = tokenStrings.subList(i, Math.min(i + FCM_BATCH_SIZE, tokenStrings.size()));
            MulticastMessage message = MulticastMessage.builder()
                    .setNotification(notification)
                    .setWebpushConfig(webpushConfig)
                    .addAllTokens(batch)
                    .build();
            try {
                BatchResponse response = messaging.sendEachForMulticast(message);
                sent += response.getSuccessCount();
                handleStaleTokens(response, batch, provider.getId());
            } catch (FirebaseMessagingException e) {
                log.error("FCM batch send failed for provider {}: {}", provider.getDisplayName(), e.getMessage());
            }
        }

        log.info("Sent FCM notification to {}/{} tokens (provider: {})",
                sent, tokens.size(), provider.getDisplayName());
        return sent;
    }

    /**
     * Send a notification to all devices belonging to a specific user.
     */
    public int sendToUser(UUID userId, String title, String body) {
        NotificationProvider provider = providerService.getDefaultProvider();
        if (provider.getProviderType() != NotificationProviderType.FCM) {
            throw new IllegalStateException("Default provider is not FCM");
        }

        List<DeviceToken> tokens = tokenRepository.findByUserId(userId);

        // Filter out tokens linked to devices with notifications disabled
        List<UUID> disabledDeviceIds = userDeviceService.getDisabledDeviceIds();
        if (!disabledDeviceIds.isEmpty()) {
            tokens = tokens.stream()
                    .filter(t -> t.getDeviceId() == null || !disabledDeviceIds.contains(t.getDeviceId()))
                    .toList();
        }

        if (tokens.isEmpty()) return 0;

        return sendTokenList(provider, tokens.stream().map(DeviceToken::getToken).toList(), title, body);
    }

    /**
     * Send a notification to a specific list of FCM tokens using the default provider.
     */
    private int sendTokenList(NotificationProvider provider, List<String> tokenStrings, String title, String body) {
        FirebaseApp app = getOrCreateApp(provider);
        FirebaseMessaging messaging = FirebaseMessaging.getInstance(app);

        Notification notification = Notification.builder()
                .setTitle(title)
                .setBody(body)
                .setImage("/dev/logo.png")
                .build();

        WebpushConfig webpushConfig = WebpushConfig.builder()
                .setNotification(WebpushNotification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .setIcon("/dev/logo.png")
                        .setBadge("/dev/logo.png")
                        .setTag("clawops")
                        .setRenotify(true)
                        .build())
                .build();

        int sent = 0;
        for (int i = 0; i < tokenStrings.size(); i += FCM_BATCH_SIZE) {
            List<String> batch = tokenStrings.subList(i, Math.min(i + FCM_BATCH_SIZE, tokenStrings.size()));
            MulticastMessage message = MulticastMessage.builder()
                    .setNotification(notification)
                    .setWebpushConfig(webpushConfig)
                    .addAllTokens(batch)
                    .build();
            try {
                BatchResponse response = messaging.sendEachForMulticast(message);
                sent += response.getSuccessCount();
                handleStaleTokens(response, batch, provider.getId());
            } catch (FirebaseMessagingException e) {
                log.error("FCM batch send failed: {}", e.getMessage());
            }
        }
        return sent;
    }

    /**
     * Validates that the FCM provider's credentials are valid by initializing a FirebaseApp.
     * Returns the project ID on success.
     */
    public String validateCredentials(NotificationProvider provider) {
        if (provider.getCredentialId() == null) {
            throw new IllegalStateException("Provider has no credential configured");
        }

        String serviceAccountJson = secretService.decryptSecret(provider.getCredentialId());
        try {
            GoogleCredentials credentials = GoogleCredentials
                    .fromStream(new ByteArrayInputStream(serviceAccountJson.getBytes(StandardCharsets.UTF_8)))
                    .createScoped(List.of("https://www.googleapis.com/auth/firebase.messaging"));
            // Force a token refresh to verify the credentials are actually valid
            credentials.refreshIfExpired();

            String appName = "validate-" + provider.getId();
            FirebaseApp testApp = null;
            try {
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(credentials)
                        .build();
                testApp = FirebaseApp.initializeApp(options, appName);
                return testApp.getOptions().getProjectId();
            } finally {
                if (testApp != null) {
                    testApp.delete();
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Invalid Firebase credentials: " + e.getMessage(), e);
        }
    }

    public void evictApp(UUID providerId) {
        FirebaseApp app = appCache.remove(providerId);
        if (app != null) {
            app.delete();
        }
    }

    private void handleStaleTokens(BatchResponse response, List<String> tokens, UUID providerId) {
        List<SendResponse> responses = response.getResponses();
        for (int i = 0; i < responses.size(); i++) {
            if (!responses.get(i).isSuccessful()) {
                FirebaseMessagingException ex = responses.get(i).getException();
                String tokenPrefix = tokens.get(i).substring(0, Math.min(20, tokens.get(i).length()));
                if (ex != null && (ex.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED
                        || ex.getMessagingErrorCode() == MessagingErrorCode.INVALID_ARGUMENT)) {
                    tokenRepository.deleteByTokenAndProviderId(tokens.get(i), providerId);
                    log.info("Removed stale FCM token ({}): {}...", ex.getMessagingErrorCode(), tokenPrefix);
                } else if (ex != null) {
                    log.warn("FCM send failed for token {}...: {} ({})",
                            tokenPrefix, ex.getMessage(), ex.getMessagingErrorCode());
                }
            }
        }
    }

    private FirebaseApp getOrCreateApp(NotificationProvider provider) {
        return appCache.computeIfAbsent(provider.getId(), id -> {
            if (provider.getCredentialId() == null) {
                throw new IllegalStateException("FCM provider '%s' has no credential configured"
                        .formatted(provider.getDisplayName()));
            }

            String serviceAccountJson = secretService.decryptSecret(provider.getCredentialId());
            try {
                GoogleCredentials credentials = GoogleCredentials
                        .fromStream(new ByteArrayInputStream(serviceAccountJson.getBytes(StandardCharsets.UTF_8)));
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(credentials)
                        .build();
                return FirebaseApp.initializeApp(options, id.toString());
            } catch (IOException e) {
                throw new IllegalStateException("Failed to initialize Firebase for provider "
                        + provider.getDisplayName(), e);
            }
        });
    }
}
