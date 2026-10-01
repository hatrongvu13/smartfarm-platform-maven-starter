package com.htv.smartfarm.security.core;

import java.util.Set;

/**
 * Transport-neutral authenticated identity produced after JWT validation.
 *
 * <p>This model contains no raw token and no transport-specific objects.
 */
public record SecurityIdentity(
        String subject,
        String clientId,
        String tenantId,
        Set<String> authorities,
        Set<String> audiences,
        TokenType tokenType
) {

    public SecurityIdentity {
        subject = requireText(subject, "subject");
        clientId = normalizeNullable(clientId);
        tenantId = requireText(tenantId, "tenantId");
        authorities = authorities == null
                ? Set.of()
                : Set.copyOf(authorities);
        audiences = audiences == null
                ? Set.of()
                : Set.copyOf(audiences);
        tokenType = tokenType == null
                ? TokenType.UNKNOWN
                : tokenType;
    }

    public boolean hasAuthority(String authority) {
        return authority != null
                && authorities.contains(authority);
    }

    public boolean hasAudience(String audience) {
        return audience != null
                && audiences.contains(audience);
    }

    public boolean isUser() {
        return tokenType == TokenType.USER;
    }

    public boolean isService() {
        return tokenType == TokenType.SERVICE;
    }

    public boolean isSuperAdmin() {
        return authorities.contains("SCOPE_*");
    }

    private static String normalizeNullable(String value) {
        return value == null || value.isBlank()
                ? null
                : value.trim();
    }

    private static String requireText(
            String value,
            String field
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    field + " must not be blank"
            );
        }

        return value.trim();
    }
}
