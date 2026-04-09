package com.openclaw.manager.openclawserversmanager.notifications.service;

import com.openclaw.manager.openclawserversmanager.notifications.entity.NotificationProvider;
import com.openclaw.manager.openclawserversmanager.notifications.entity.NotificationProviderType;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class NotificationDispatchService {

    private final Map<NotificationProviderType, NotificationSender> senders;
    private final NotificationProviderService providerService;
    private final FirebaseService firebaseService;

    public NotificationDispatchService(List<NotificationSender> senderList,
                                       NotificationProviderService providerService,
                                       FirebaseService firebaseService) {
        this.senders = senderList.stream()
                .collect(Collectors.toMap(NotificationSender::getProviderType, Function.identity()));
        this.providerService = providerService;
        this.firebaseService = firebaseService;
    }

    /** Send notification to all subscribers of the default provider. */
    public int sendToDefault(String title, String body) {
        NotificationProvider provider = providerService.getDefaultProvider();
        return sendToProvider(provider, title, body);
    }

    /** Send notification via a specific provider. */
    public int sendToProvider(NotificationProvider provider, String title, String body) {
        NotificationSender sender = senders.get(provider.getProviderType());
        if (sender == null) {
            throw new IllegalStateException("No sender registered for provider type: " + provider.getProviderType());
        }
        return sender.sendToProvider(provider, title, body);
    }

    /** Send notification to all devices of a specific user. */
    public int sendToUser(UUID userId, String title, String body) {
        return firebaseService.sendToUser(userId, title, body);
    }

    /** Send notification to all subscribers across ALL enabled providers. */
    public int sendToAll(String title, String body) {
        int total = 0;
        var providers = providerService.getAllEnabledProviders();
        for (var provider : providers) {
            NotificationSender sender = senders.get(provider.getProviderType());
            if (sender != null) {
                total += sender.sendToProvider(provider, title, body);
            }
        }
        return total;
    }
}
