package com.banryeokkurumi.ordering;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Component
public class PiiCipher {
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private final byte[] key;
    private final SecureRandom secureRandom = new SecureRandom();

    public PiiCipher(@Value("${app.pii.encryption-key:}") String encodedKey, Environment environment) {
        if (encodedKey.isBlank()) {
            if (Arrays.asList(environment.getActiveProfiles()).contains("public")) {
                throw new IllegalStateException("public 프로필에는 PII_ENCRYPTION_KEY가 필요합니다.");
            }
            key = new byte[0];
            return;
        }
        byte[] decoded = Base64.getDecoder().decode(encodedKey);
        if (decoded.length != 32) throw new IllegalArgumentException("PII_ENCRYPTION_KEY는 Base64 인코딩된 32바이트 키여야 합니다.");
        key = decoded;
    }

    public String encrypt(String plainText) {
        if (key.length == 0) throw new IllegalStateException("PII_ENCRYPTION_KEY가 필요합니다.");
        byte[] iv = new byte[IV_LENGTH];
        secureRandom.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("개인정보 암호화에 실패했습니다.", exception);
        }
    }

    public String decrypt(String cipherText) {
        if (key.length == 0) throw new IllegalStateException("PII_ENCRYPTION_KEY가 필요합니다.");
        byte[] payload = Base64.getDecoder().decode(cipherText);
        byte[] iv = Arrays.copyOfRange(payload, 0, IV_LENGTH);
        byte[] encrypted = Arrays.copyOfRange(payload, IV_LENGTH, payload.length);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("개인정보 복호화에 실패했습니다.", exception);
        }
    }
}
