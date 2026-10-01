package com.htv.smartfarm.identity.mfa.crypto;

import java.io.ByteArrayOutputStream;

public final class Base32Support {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private Base32Support() {
    }

    public static String encode(byte[] source) {
        StringBuilder result = new StringBuilder();
        int buffer = 0;
        int bitsLeft = 0;
        for (byte value : source) {
            buffer = (buffer << 8) | (value & 0xff);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                result.append(ALPHABET.charAt((buffer >> (bitsLeft - 5)) & 31));
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            result.append(ALPHABET.charAt((buffer << (5 - bitsLeft)) & 31));
        }
        return result.toString();
    }

    public static byte[] decode(String source) {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("Base32 value must not be blank");
        }
        int buffer = 0;
        int bitsLeft = 0;
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        for (char raw : source.replace("=", "").toUpperCase().toCharArray()) {
            int value = ALPHABET.indexOf(raw);
            if (value < 0) throw new IllegalArgumentException("Invalid Base32 value");
            buffer = (buffer << 5) | value;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                result.write((buffer >> (bitsLeft - 8)) & 255);
                bitsLeft -= 8;
            }
        }
        return result.toByteArray();
    }
}
