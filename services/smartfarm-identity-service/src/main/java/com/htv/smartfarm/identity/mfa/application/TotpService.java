package com.htv.smartfarm.identity.mfa.application;

import java.nio.ByteBuffer;
import java.time.Instant;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.htv.smartfarm.identity.mfa.crypto.Base32Support;

import org.springframework.stereotype.Service;

@Service
public class TotpService {

    private static final long PERIOD_SECONDS = 30;
    private static final int DIGITS = 6;

    public long verify(String base32Secret, String code, Instant now) {
        if (code == null || !code.matches("[0-9]{6}")) return -1;
        long currentStep = now.getEpochSecond() / PERIOD_SECONDS;
        for (long step = currentStep - 1; step <= currentStep + 1; step++) {
            if (constantTimeEquals(generate(base32Secret, step), code)) return step;
        }
        return -1;
    }

    private String generate(String secret, long timeStep) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(Base32Support.decode(secret), "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(timeStep).array());
            int offset = hash[hash.length - 1] & 15;
            int binary = ((hash[offset] & 127) << 24)
                    | ((hash[offset + 1] & 255) << 16)
                    | ((hash[offset + 2] & 255) << 8)
                    | (hash[offset + 3] & 255);
            int value = binary % 1_000_000;
            return String.format("%0" + DIGITS + "d", value);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not calculate TOTP", exception);
        }
    }

    private boolean constantTimeEquals(String expected, String actual) {
        if (expected.length() != actual.length()) return false;
        int difference = 0;
        for (int index = 0; index < expected.length(); index++) {
            difference |= expected.charAt(index) ^ actual.charAt(index);
        }
        return difference == 0;
    }
}
