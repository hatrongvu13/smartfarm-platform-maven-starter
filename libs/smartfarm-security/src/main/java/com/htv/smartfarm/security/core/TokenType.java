package com.htv.smartfarm.security.core;

import java.util.Locale;

/**
 * Type of the authenticated JWT principal.
 *
 * <p>UNKNOWN is used to reject tokens that omit or misstate their token_type.
 */
public enum TokenType {
    USER,
    SERVICE,
    UNKNOWN;

    public static TokenType fromClaim(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }

        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "USER" -> USER;
            case "SERVICE", "CLIENT_CREDENTIALS" -> SERVICE;
            default -> UNKNOWN;
        };
    }
}
