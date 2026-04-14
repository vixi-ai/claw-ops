package com.openclaw.manager.openclawserversmanager.monitoring.service;

import com.openclaw.manager.openclawserversmanager.common.exception.ResourceNotFoundException;
import com.openclaw.manager.openclawserversmanager.monitoring.entity.NotificationChannel;
import com.openclaw.manager.openclawserversmanager.monitoring.entity.NotificationChannelType;
import com.openclaw.manager.openclawserversmanager.monitoring.repository.NotificationChannelRepository;
import com.openclaw.manager.openclawserversmanager.secrets.dto.EncryptedPayload;
import com.openclaw.manager.openclawserversmanager.secrets.service.EncryptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class NotificationChannelService {

    private static final Logger log = LoggerFactory.getLogger(NotificationChannelService.class);

    private final NotificationChannelRepository channelRepository;
    private final EncryptionService encryptionService;

    public NotificationChannelService(NotificationChannelRepository channelRepository,
                                       EncryptionService encryptionService) {
        this.channelRepository = channelRepository;
        this.encryptionService = encryptionService;
    }

    @Transactional(readOnly = true)
    public List<NotificationChannel> listChannels() {
        return channelRepository.findAll();
    }

    @Transactional(readOnly = true)
    public NotificationChannel getChannel(UUID id) {
        return channelRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification channel not found: " + id));
    }

    @Transactional
    public NotificationChannel createChannel(String name, NotificationChannelType type, String configJson) {
        if (channelRepository.existsByName(name)) {
            throw new IllegalArgumentException("Channel with name '" + name + "' already exists");
        }

        EncryptedPayload encrypted = encryptionService.encrypt(configJson);

        NotificationChannel channel = new NotificationChannel();
        channel.setName(name);
        channel.setChannelType(type);
        channel.setConfig(encrypted.ciphertext());
        channel.setConfigIv(encrypted.iv());
        channel.setEnabled(true);

        return channelRepository.save(channel);
    }

    @Transactional
    public NotificationChannel updateChannel(UUID id, String name, Boolean enabled, String configJson) {
        NotificationChannel channel = getChannel(id);

        if (name != null) {
            if (!name.equals(channel.getName()) && channelRepository.existsByName(name)) {
                throw new IllegalArgumentException("Channel with name '" + name + "' already exists");
            }
            channel.setName(name);
        }
        if (enabled != null) channel.setEnabled(enabled);
        if (configJson != null) {
            EncryptedPayload encrypted = encryptionService.encrypt(configJson);
            channel.setConfig(encrypted.ciphertext());
            channel.setConfigIv(encrypted.iv());
        }

        return channelRepository.save(channel);
    }

    @Transactional
    public void deleteChannel(UUID id) {
        if (!channelRepository.existsById(id)) {
            throw new ResourceNotFoundException("Notification channel not found: " + id);
        }
        channelRepository.deleteById(id);
    }

    /**
     * Send a test notification to verify channel configuration works.
     */
    @Transactional(readOnly = true)
    public void testChannel(UUID id) {
        NotificationChannel channel = getChannel(id);
        String configJson = encryptionService.decrypt(channel.getConfig(), channel.getConfigIv());
        log.info("Test notification for channel '{}' (type={}): config decrypted successfully, length={}",
                channel.getName(), channel.getChannelType(), configJson.length());
        // Actual test dispatch is handled by AlertNotificationService patterns.
        // This method validates that decryption works and config is readable.
    }
}
