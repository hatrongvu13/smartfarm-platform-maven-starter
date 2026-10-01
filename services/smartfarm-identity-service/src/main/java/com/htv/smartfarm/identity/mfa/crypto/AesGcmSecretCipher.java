package com.htv.smartfarm.identity.mfa.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import com.htv.smartfarm.identity.mfa.config.MfaProperties;

import org.springframework.stereotype.Component;

@Component
public class AesGcmSecretCipher implements SecretCipher {

    private static final byte FORMAT_VERSION = 1;
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final MfaProperties properties;
    private final SecureRandom random = new SecureRandom();

    public AesGcmSecretCipher(MfaProperties properties) {
        this.properties = properties;
    }

    @Override
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            throw new IllegalArgumentException("plaintext must not be blank");
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            ByteBuffer result = ByteBuffer.allocate(1 + iv.length + encrypted.length);
            result.put(FORMAT_VERSION).put(iv).put(encrypted);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(result.array());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Could not encrypt MFA secret", exception);
        }
    }

    @Override
    public String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isBlank()) {
            throw new IllegalArgumentException("ciphertext must not be blank");
        }
        try {
            ByteBuffer source = ByteBuffer.wrap(Base64.getUrlDecoder().decode(ciphertext));
            if (source.get() != FORMAT_VERSION) {
                throw new IllegalArgumentException("Unsupported ciphertext format");
            }
            byte[] iv = new byte[IV_LENGTH];
            source.get(iv);
            byte[] encrypted = new byte[source.remaining()];
            source.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("Could not decrypt MFA secret", exception);
        }
    }

    private SecretKeySpec key() {
        String configured = properties.getEncryptionKey();
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException("IDENTITY_MFA_ENCRYPTION_KEY is required for MFA operations");
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(configured.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("MFA encryption key must be Base64", exception);
        }
        if (decoded.length != 32) {
            throw new IllegalStateException("MFA encryption key must decode to 32 bytes");
        }
        return new SecretKeySpec(decoded, "AES");
    }
}
