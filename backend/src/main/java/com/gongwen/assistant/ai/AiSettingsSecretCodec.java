package com.gongwen.assistant.ai;

import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class AiSettingsSecretCodec {
    private static final String PREFIX = "v1:";
    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final Path keyFile;
    private final SecureRandom secureRandom = new SecureRandom();
    private byte[] keyBytes;

    public AiSettingsSecretCodec(AiRuntimeProperties properties) {
        this.keyFile = Path.of(properties.settingsKeyFile()).toAbsolutePath().normalize();
    }

    public String encrypt(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(value.strip().getBytes(StandardCharsets.UTF_8));
            return PREFIX
                    + Base64.getEncoder().encodeToString(iv)
                    + ":"
                    + Base64.getEncoder().encodeToString(encrypted);
        } catch (GeneralSecurityException exception) {
            throw new AiSettingsException("AI_SETTINGS_SECRET_ENCRYPT_FAILED", "AI Key 加密失败");
        }
    }

    public String decrypt(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        if (!value.startsWith(PREFIX)) {
            return value;
        }
        try {
            String[] parts = value.split(":", 3);
            if (parts.length != 3) {
                throw new AiSettingsException("AI_SETTINGS_SECRET_INVALID", "AI Key 存储格式无效");
            }
            byte[] iv = Base64.getDecoder().decode(parts[1]);
            byte[] encrypted = Base64.getDecoder().decode(parts[2]);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec(), new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new AiSettingsException("AI_SETTINGS_SECRET_DECRYPT_FAILED", "AI Key 解密失败，请检查本机密钥文件");
        }
    }

    private SecretKeySpec keySpec() {
        return new SecretKeySpec(loadOrCreateKey(), "AES");
    }

    private synchronized byte[] loadOrCreateKey() {
        if (keyBytes != null) {
            return keyBytes;
        }
        try {
            if (Files.exists(keyFile)) {
                keyBytes = Base64.getDecoder().decode(Files.readString(keyFile, StandardCharsets.UTF_8).strip());
                if (keyBytes.length != KEY_BYTES) {
                    throw new AiSettingsException("AI_SETTINGS_KEY_INVALID", "AI 设置密钥文件无效");
                }
                return keyBytes;
            }
            byte[] generated = new byte[KEY_BYTES];
            secureRandom.nextBytes(generated);
            Path parent = keyFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(keyFile, Base64.getEncoder().encodeToString(generated), StandardCharsets.UTF_8);
            keyBytes = generated;
            return keyBytes;
        } catch (IOException | IllegalArgumentException exception) {
            throw new AiSettingsException("AI_SETTINGS_KEY_UNAVAILABLE", "AI 设置密钥文件不可用");
        }
    }
}
