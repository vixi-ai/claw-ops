package com.openclaw.manager.openclawserversmanager.notifications.service;

import com.openclaw.manager.openclawserversmanager.notifications.entity.NotificationProvider;
import com.openclaw.manager.openclawserversmanager.notifications.entity.NotificationProviderType;

public interface NotificationSender {

    NotificationProviderType getProviderType();

    int sendToProvider(NotificationProvider provider, String title, String body);
}
