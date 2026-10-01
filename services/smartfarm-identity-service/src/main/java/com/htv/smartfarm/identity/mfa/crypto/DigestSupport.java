package com.htv.smartfarm.identity.mfa.crypto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class DigestSupport {

    private DigestSupport() {
    }

    public static String sha256(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(raw.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
