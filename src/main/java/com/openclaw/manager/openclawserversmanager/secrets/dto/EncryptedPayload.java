package com.openclaw.manager.openclawserversmanager.secrets.dto;

public record EncryptedPayload(byte[] ciphertext, byte[] iv) {
}
