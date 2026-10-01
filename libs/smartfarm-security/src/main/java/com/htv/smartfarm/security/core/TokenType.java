package com.htv.smartfarm.security.core;

import java.util.Locale;

/**
 * Type of the authenticated JWT principal.
 *
 * <p>UNKNOWN is retained only during the token-contract migration. Once all
 * token issuers emit token_type, UNKNOWN should be rejected by internal APIs.
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
