package com.htv.farmverse.security.core;

import java.util.Optional;
import java.util.Set;

/** Transport-neutral authenticated identity produced after JWT validation. */
public record SecurityIdentity(
        String subject,
        String clientId,
        String tenantId,
        Set<String> authorities,
        Set<String> audiences,
        TokenType tokenType,
        Long authorizationVersion,
        Long tenantAuthorizationVersion,
        DelegatedActor delegatedActor
) {
    /** Compatibility constructor retained for existing callers and tests. */
    public SecurityIdentity(
            String subject,
            String clientId,
            String tenantId,
            Set<String> authorities,
            Set<String> audiences,
            TokenType tokenType
    ) {
        this(subject, clientId, tenantId, authorities, audiences, tokenType, null, null, null);
    }

    public SecurityIdentity {
        subject = requireText(subject, "subject");
        clientId = normalizeNullable(clientId);
        tenantId = requireText(tenantId, "tenantId");
        authorities = authorities == null ? Set.of() : Set.copyOf(authorities);
        audiences = audiences == null ? Set.of() : Set.copyOf(audiences);
        tokenType = tokenType == null ? TokenType.UNKNOWN : tokenType;
        validateVersion(authorizationVersion, "authorizationVersion");
        validateVersion(tenantAuthorizationVersion, "tenantAuthorizationVersion");

        if (tokenType == TokenType.USER && delegatedActor != null) {
            throw new IllegalArgumentException("user tokens must not contain a delegated actor");
        }
        if (delegatedActor != null && tokenType != TokenType.SERVICE) {
            throw new IllegalArgumentException("delegated actor requires a service token");
        }
    }

    public boolean hasAuthority(String authority) {
        return authority != null && authorities.contains(authority);
    }

    public boolean hasAudience(String audience) {
        return audience != null && audiences.contains(audience);
    }

    public boolean isUser() { return tokenType == TokenType.USER; }
    public boolean isService() { return tokenType == TokenType.SERVICE; }

    public boolean isSuperAdmin() {
        return isUser() && authorities.contains("SCOPE_*");
    }

    /** Machine/user subject authenticated by the JWT itself. */
    public String machineSubject() { return subject; }

    /** Human actor for delegated service tokens, otherwise the JWT subject. */
    public String effectiveActorSubject() {
        return delegatedActor == null ? subject : delegatedActor.subject();
    }

    public Optional<DelegatedActor> delegatedActorOptional() {
        return Optional.ofNullable(delegatedActor);
    }

    private static void validateVersion(Long value, String field) {
        if (value != null && value < 0) {
            throw new IllegalArgumentException(field + " must not be negative");
        }
    }

    private static String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
