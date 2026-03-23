package com.openclaw.manager.openclawserversmanager.secrets.service;

import com.openclaw.manager.openclawserversmanager.common.exception.EncryptionException;
import com.openclaw.manager.openclawserversmanager.secrets.dto.EncryptedPayload;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class EncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH = 128;

    private final SecretKey masterKey;

    public EncryptionService(@Value("${MASTER_ENCRYPTION_KEY:}") String keyBase64) {
        if (keyBase64 == null || keyBase64.isBlank()) {
            throw new EncryptionException(
                    "MASTER_ENCRYPTION_KEY is not set or invalid. Application cannot start without an encryption key.");
        }
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(keyBase64);
        } catch (IllegalArgumentException e) {
            throw new EncryptionException("MASTER_ENCRYPTION_KEY is not valid base64", e);
        }
        if (keyBytes.length < 32) {
            throw new EncryptionException("MASTER_ENCRYPTION_KEY must be at least 256 bits (32 bytes)");
        }
        this.masterKey = new SecretKeySpec(keyBytes, 0, 32, "AES");
    }

    public EncryptedPayload encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, masterKey, new GCMParameterSpec(TAG_LENGTH, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            return new EncryptedPayload(ciphertext, iv);
        } catch (Exception e) {
            throw new EncryptionException("Failed to encrypt data", e);
        }
    }

    public String decrypt(byte[] ciphertext, byte[] iv) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, masterKey, new GCMParameterSpec(TAG_LENGTH, iv));
            byte[] plaintext = cipher.doFinal(ciphertext);

            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new EncryptionException("Failed to decrypt data", e);
        }
    }
}
