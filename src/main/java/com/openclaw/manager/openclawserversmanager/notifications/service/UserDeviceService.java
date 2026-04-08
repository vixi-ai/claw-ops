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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class UserDeviceService {

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
        UserDevice device = new UserDevice();
        device.setUserId(userId);
        device.setDeviceName(request.deviceName());
        device.setPlatform(request.platform());
        device.setNotificationsEnabled(true);
        UserDevice saved = deviceRepository.save(device);

        NotificationProvider provider = providerService.getDefaultProvider();

        // Register the push subscription or FCM token linked to this device
        if (provider.getProviderType() == NotificationProviderType.FCM && request.fcmToken() != null) {
            DeviceToken dt = deviceTokenRepository.findByTokenAndProviderId(request.fcmToken(), provider.getId())
                    .orElse(new DeviceToken());
            dt.setToken(request.fcmToken());
            dt.setPlatform(request.platform());
            dt.setUserId(userId);
            dt.setProviderId(provider.getId());
            dt.setDeviceId(saved.getId());
            deviceTokenRepository.save(dt);
        } else if (provider.getProviderType() == NotificationProviderType.WEB_PUSH
                && request.pushEndpoint() != null) {
            PushSubscription sub = pushSubscriptionRepository.findByEndpoint(request.pushEndpoint())
                    .orElse(new PushSubscription());
            sub.setEndpoint(request.pushEndpoint());
            sub.setKeyAuth(request.pushKeyAuth());
            sub.setKeyP256dh(request.pushKeyP256dh());
            sub.setUserId(userId);
            sub.setProviderId(provider.getId());
            sub.setDeviceId(saved.getId());
            pushSubscriptionRepository.save(sub);
        }

        return toResponse(saved);
    }

    public List<UserDeviceResponse> getUserDevices(UUID userId) {
        return deviceRepository.findByUserId(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public UserDeviceResponse toggleNotifications(UUID deviceId, boolean enabled, UUID userId) {
        UserDevice device = deviceRepository.findById(deviceId)
                .filter(d -> d.getUserId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Device not found: " + deviceId));
        device.setNotificationsEnabled(enabled);
        return toResponse(deviceRepository.save(device));
    }

    @Transactional
    public void removeDevice(UUID deviceId, UUID userId) {
        UserDevice device = deviceRepository.findById(deviceId)
                .filter(d -> d.getUserId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Device not found: " + deviceId));

        // Clean up linked subscriptions/tokens (device_id is SET NULL on delete, but let's be explicit)
        pushSubscriptionRepository.findByDeviceId(deviceId).ifPresent(pushSubscriptionRepository::delete);
        deviceTokenRepository.findByDeviceId(deviceId).ifPresent(deviceTokenRepository::delete);

        deviceRepository.delete(device);
    }

    /**
     * Get device IDs for a user that have notifications disabled.
     * Used by send services to filter out disabled devices.
     */
    public List<UUID> getDisabledDeviceIds() {
        // Return all device IDs that have notifications disabled
        return deviceRepository.findAll().stream()
                .filter(d -> !d.isNotificationsEnabled())
                .map(UserDevice::getId)
                .toList();
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
