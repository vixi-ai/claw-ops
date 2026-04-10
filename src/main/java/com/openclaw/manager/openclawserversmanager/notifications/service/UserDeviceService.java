package com.openclaw.manager.openclawserversmanager.notifications.service;

import com.openclaw.manager.openclawserversmanager.common.exception.ResourceNotFoundException;
import com.openclaw.manager.openclawserversmanager.notifications.dto.RegisterDeviceRequest;
import com.openclaw.manager.openclawserversmanager.notifications.dto.UserDeviceResponse;
import com.openclaw.manager.openclawserversmanager.notifications.entity.DeviceToken;
import com.openclaw.manager.openclawserversmanager.notifications.entity.NotificationProvider;
import com.openclaw.manager.openclawserversmanager.notifications.entity.NotificationProviderType;
import com.openclaw.manager.openclawserversmanager.notifications.entity.PushSubscription;
import com.openclaw.manager.openclawserversmanager.notifications.entity.UserDevice;
import com.openclaw.manager.openclawserversmanager.notifications.repository.DeviceTokenRepository;
import com.openclaw.manager.openclawserversmanager.notifications.repository.PushSubscriptionRepository;
import com.openclaw.manager.openclawserversmanager.notifications.repository.UserDeviceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class UserDeviceService {

    private static final Logger log = LoggerFactory.getLogger(UserDeviceService.class);

    private final UserDeviceRepository deviceRepository;
    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final DeviceTokenRepository deviceTokenRepository;
    private final NotificationProviderService providerService;

    public UserDeviceService(UserDeviceRepository deviceRepository,
                             PushSubscriptionRepository pushSubscriptionRepository,
                             DeviceTokenRepository deviceTokenRepository,
                             NotificationProviderService providerService) {
        this.deviceRepository = deviceRepository;
        this.pushSubscriptionRepository = pushSubscriptionRepository;
        this.deviceTokenRepository = deviceTokenRepository;
        this.providerService = providerService;
    }

    @Transactional
    public UserDeviceResponse registerDevice(RegisterDeviceRequest request, UUID userId) {
        // Upsert: reuse existing device for same user + device name
        UserDevice device = deviceRepository.findByUserIdAndDeviceName(userId, request.deviceName())
                .orElse(null);

        if (device != null) {
            device.setNotificationsEnabled(true);
            device.setPlatform(request.platform());
            device = deviceRepository.save(device);
        } else {
            device = new UserDevice();
            device.setUserId(userId);
            device.setDeviceName(request.deviceName());
            device.setPlatform(request.platform());
            device.setNotificationsEnabled(true);
            try {
                device = deviceRepository.save(device);
            } catch (DataIntegrityViolationException e) {
                // Concurrent registration — fetch existing
                device = deviceRepository.findByUserIdAndDeviceName(userId, request.deviceName())
                        .orElseThrow(() -> new ResourceNotFoundException("Device registration conflict"));
                device.setNotificationsEnabled(true);
                device = deviceRepository.save(device);
            }
        }

        NotificationProvider provider = providerService.getDefaultProvider();
        linkTokenToDevice(request.fcmToken(), request.pushEndpoint(), request.pushKeyAuth(),
                request.pushKeyP256dh(), request.platform(), userId, provider, device.getId());

        return toResponse(device);
    }

    public List<UserDeviceResponse> getUserDevices(UUID userId) {
        return deviceRepository.findByUserId(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public UserDeviceResponse toggleNotifications(UUID deviceId, boolean enabled, UUID userId,
                                                   String fcmToken, String pushEndpoint,
                                                   String pushKeyAuth, String pushKeyP256dh) {
        UserDevice device = deviceRepository.findById(deviceId)
                .filter(d -> d.getUserId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Device not found: " + deviceId));
        device.setNotificationsEnabled(enabled);
        device = deviceRepository.save(device);

        // When re-enabling with fresh token, update the linked subscription
        if (enabled) {
            NotificationProvider provider = providerService.getDefaultProvider();
            linkTokenToDevice(fcmToken, pushEndpoint, pushKeyAuth, pushKeyP256dh,
                    device.getPlatform(), userId, provider, device.getId());
        }

        return toResponse(device);
    }

    @Transactional
    public void removeDevice(UUID deviceId, UUID userId) {
        UserDevice device = deviceRepository.findById(deviceId)
                .filter(d -> d.getUserId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Device not found: " + deviceId));

        pushSubscriptionRepository.findByDeviceId(deviceId).ifPresent(pushSubscriptionRepository::delete);
        deviceTokenRepository.findByDeviceId(deviceId).ifPresent(deviceTokenRepository::delete);

        deviceRepository.delete(device);
    }

    public List<UUID> getDisabledDeviceIds() {
        return deviceRepository.findAll().stream()
                .filter(d -> !d.isNotificationsEnabled())
                .map(UserDevice::getId)
                .toList();
    }

    // ── Private helpers ──

    private void linkTokenToDevice(String fcmToken, String pushEndpoint, String pushKeyAuth,
                                    String pushKeyP256dh, String platform, UUID userId,
                                    NotificationProvider provider, UUID deviceId) {
        if (provider.getProviderType() == NotificationProviderType.FCM
                && fcmToken != null && !fcmToken.isBlank()) {
            upsertDeviceToken(fcmToken, platform, userId, provider, deviceId);
        } else if (provider.getProviderType() == NotificationProviderType.WEB_PUSH
                && pushEndpoint != null && !pushEndpoint.isBlank()) {
            upsertPushSubscription(pushEndpoint, pushKeyAuth, pushKeyP256dh, userId, provider, deviceId);
        }
    }

    private void upsertDeviceToken(String token, String platform, UUID userId,
                                    NotificationProvider provider, UUID deviceId) {
        // Remove any old token linked to this device (token refresh)
        deviceTokenRepository.findByDeviceId(deviceId).ifPresent(deviceTokenRepository::delete);

        DeviceToken dt = deviceTokenRepository.findByTokenAndProviderId(token, provider.getId())
                .orElse(new DeviceToken());
        dt.setToken(token);
        dt.setPlatform(platform);
        dt.setUserId(userId);
        dt.setProviderId(provider.getId());
        dt.setDeviceId(deviceId);
        deviceTokenRepository.save(dt);
        log.info("Linked FCM token to device {} (provider: {})", deviceId, provider.getDisplayName());
    }

    private void upsertPushSubscription(String endpoint, String keyAuth, String keyP256dh,
                                         UUID userId, NotificationProvider provider, UUID deviceId) {
        // Remove any old subscription linked to this device
        pushSubscriptionRepository.findByDeviceId(deviceId).ifPresent(pushSubscriptionRepository::delete);

        PushSubscription sub = pushSubscriptionRepository.findByEndpoint(endpoint)
                .orElse(new PushSubscription());
        sub.setEndpoint(endpoint);
        sub.setKeyAuth(keyAuth);
        sub.setKeyP256dh(keyP256dh);
        sub.setUserId(userId);
        sub.setProviderId(provider.getId());
        sub.setDeviceId(deviceId);
        pushSubscriptionRepository.save(sub);
        log.info("Linked push subscription to device {} (provider: {})", deviceId, provider.getDisplayName());
    }

    private UserDeviceResponse toResponse(UserDevice device) {
        return new UserDeviceResponse(
                device.getId(),
                device.getDeviceName(),
                device.getPlatform(),
                device.isNotificationsEnabled(),
                device.getCreatedAt(),
                device.getUpdatedAt()
        );
    }
}
