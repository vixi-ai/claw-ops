package com.openclaw.manager.openclawserversmanager.notifications.service;

import com.openclaw.manager.openclawserversmanager.audit.entity.AuditAction;
import com.openclaw.manager.openclawserversmanager.audit.service.AuditService;
import com.openclaw.manager.openclawserversmanager.common.exception.DuplicateResourceException;
import com.openclaw.manager.openclawserversmanager.common.exception.ResourceNotFoundException;
import com.openclaw.manager.openclawserversmanager.notifications.dto.CreateNotificationProviderRequest;
import com.openclaw.manager.openclawserversmanager.notifications.dto.NotificationProviderResponse;
import com.openclaw.manager.openclawserversmanager.notifications.dto.UpdateNotificationProviderRequest;
import com.openclaw.manager.openclawserversmanager.notifications.entity.NotificationProvider;
import com.openclaw.manager.openclawserversmanager.notifications.entity.NotificationProviderType;
import com.openclaw.manager.openclawserversmanager.notifications.mapper.NotificationProviderMapper;
import com.openclaw.manager.openclawserversmanager.notifications.repository.DeviceTokenRepository;
import com.openclaw.manager.openclawserversmanager.notifications.repository.NotificationProviderRepository;
import com.openclaw.manager.openclawserversmanager.notifications.repository.PushSubscriptionRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class NotificationProviderService {

    private final NotificationProviderRepository providerRepository;
    private final PushSubscriptionRepository subscriptionRepository;
    private final DeviceTokenRepository deviceTokenRepository;
    private final AuditService auditService;
    private final FirebaseService firebaseService;

    public NotificationProviderService(NotificationProviderRepository providerRepository,
                                       PushSubscriptionRepository subscriptionRepository,
                                       DeviceTokenRepository deviceTokenRepository,
                                       AuditService auditService,
                                       @Lazy FirebaseService firebaseService) {
        this.providerRepository = providerRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.deviceTokenRepository = deviceTokenRepository;
        this.auditService = auditService;
        this.firebaseService = firebaseService;
    }

    @Transactional
    public NotificationProviderResponse createProvider(CreateNotificationProviderRequest request, UUID userId) {
        if (providerRepository.existsByDisplayName(request.displayName())) {
            throw new DuplicateResourceException("Notification provider with name '%s' already exists"
                    .formatted(request.displayName()));
        }

        NotificationProvider entity = NotificationProviderMapper.toEntity(request);

        // First provider becomes default automatically
        if (providerRepository.count() == 0) {
            entity.setDefault(true);
        }

        NotificationProvider saved = providerRepository.save(entity);

        auditService.log(AuditAction.NOTIFICATION_PROVIDER_CREATED, "NOTIFICATION_PROVIDER",
                saved.getId(), userId,
                "Notification provider '%s' created (type: %s)".formatted(saved.getDisplayName(), saved.getProviderType()));

        return NotificationProviderMapper.toResponse(saved);
    }

    public Page<NotificationProviderResponse> getAllProviders(Pageable pageable) {
        return providerRepository.findAll(pageable).map(NotificationProviderMapper::toResponse);
    }

    public NotificationProviderResponse getProviderById(UUID id) {
        return NotificationProviderMapper.toResponse(findProviderOrThrow(id));
    }

    @Transactional
    public NotificationProviderResponse updateProvider(UUID id, UpdateNotificationProviderRequest request, UUID userId) {
        NotificationProvider provider = findProviderOrThrow(id);

        if (request.displayName() != null && !request.displayName().equals(provider.getDisplayName())) {
            if (providerRepository.existsByDisplayName(request.displayName())) {
                throw new DuplicateResourceException("Name '%s' already taken".formatted(request.displayName()));
            }
            provider.setDisplayName(request.displayName());
        }

        if (request.enabled() != null) {
            provider.setEnabled(request.enabled());
        }

        if (request.credentialId() != null) {
            provider.setCredentialId(request.credentialId());
        }

        if (request.providerSettings() != null) {
            provider.setProviderSettings(NotificationProviderMapper.serializeSettings(request.providerSettings()));
        }

        // Evict cached FirebaseApp if credentials or settings changed for an FCM provider
        if (provider.getProviderType() == NotificationProviderType.FCM
                && (request.credentialId() != null || request.providerSettings() != null)) {
            firebaseService.evictApp(id);
        }

        NotificationProvider saved = providerRepository.save(provider);

        auditService.log(AuditAction.NOTIFICATION_PROVIDER_UPDATED, "NOTIFICATION_PROVIDER",
                saved.getId(), userId,
                "Notification provider '%s' updated".formatted(saved.getDisplayName()));

        return NotificationProviderMapper.toResponse(saved);
    }

    @Transactional
    public void deleteProvider(UUID id, UUID userId) {
        NotificationProvider provider = findProviderOrThrow(id);

        // Delete all subscriptions/tokens tied to this provider
        subscriptionRepository.deleteAll(subscriptionRepository.findByProviderId(id));
        deviceTokenRepository.deleteByProviderId(id);

        // Evict cached FirebaseApp if this is an FCM provider
        if (provider.getProviderType() == NotificationProviderType.FCM) {
            firebaseService.evictApp(id);
        }

        providerRepository.delete(provider);

        auditService.log(AuditAction.NOTIFICATION_PROVIDER_DELETED, "NOTIFICATION_PROVIDER",
                id, userId,
                "Notification provider '%s' deleted".formatted(provider.getDisplayName()));
    }

    @Transactional
    public NotificationProviderResponse setDefault(UUID id, UUID userId) {
        NotificationProvider provider = findProviderOrThrow(id);

        providerRepository.clearDefault();
        provider.setDefault(true);
        NotificationProvider saved = providerRepository.save(provider);

        auditService.log(AuditAction.NOTIFICATION_PROVIDER_SET_DEFAULT, "NOTIFICATION_PROVIDER",
                saved.getId(), userId,
                "Notification provider '%s' set as default".formatted(saved.getDisplayName()));

        return NotificationProviderMapper.toResponse(saved);
    }

    public NotificationProvider getDefaultProvider() {
        return providerRepository.findByIsDefaultTrue()
                .orElseThrow(() -> new ResourceNotFoundException("No default notification provider configured"));
    }

    public NotificationProvider getDefaultFcmProvider() {
        return providerRepository.findFirstByProviderTypeAndEnabledTrue(NotificationProviderType.FCM)
                .orElseThrow(() -> new ResourceNotFoundException("No enabled FCM provider configured"));
    }

    public List<NotificationProvider> getAllEnabledProviders() {
        return providerRepository.findByEnabledTrue();
    }

    public NotificationProvider getProviderEntity(UUID id) {
        return findProviderOrThrow(id);
    }

    private NotificationProvider findProviderOrThrow(UUID id) {
        return providerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification provider not found: " + id));
    }
}
