package com.htv.smartfarm.identity.token.grant;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.htv.smartfarm.common.contract.TokenClaimContract;
import java.util.Objects;
import java.util.Set;

public record TokenContext(
        GrantType grantType,
        String subjectId,
        String tenantId,
        String audience,
        String clientId,
        String actorSubjectId,
        Set<String> scopes,
        Set<String> roles,
        long subjectAuthorizationVersion,
        long tenantAuthorizationVersion,
        String sessionId,
        Instant authenticatedAt,
        List<String> authenticationMethods,
        Duration ttl
) {
    public TokenContext {
        grantType = Objects.requireNonNull(grantType, "grantType");
        subjectId = required(subjectId, "subjectId");
        tenantId = required(tenantId, "tenantId");
        audience = required(audience, "audience");
        scopes = scopes == null ? Set.of() : Set.copyOf(scopes);
        roles = roles == null ? Set.of() : Set.copyOf(roles);
        authenticationMethods = authenticationMethods == null
                ? List.of() : List.copyOf(authenticationMethods);
        ttl = Objects.requireNonNull(ttl, "ttl");
        if (ttl.isZero() || ttl.isNegative()) throw new IllegalArgumentException("ttl must be positive");
        if (subjectAuthorizationVersion < 0 || tenantAuthorizationVersion < 0) {
            throw new IllegalArgumentException("authorization versions must not be negative");
        }
        boolean service = grantType == GrantType.SERVICE_CLIENT
                || grantType == GrantType.DELEGATED_SERVICE;
        if (service && (clientId == null || clientId.isBlank())) {
            throw new IllegalArgumentException("clientId is required for service grants");
        }
        if (grantType == GrantType.DELEGATED_SERVICE
                && (actorSubjectId == null || actorSubjectId.isBlank())) {
            throw new IllegalArgumentException("actorSubjectId is required for delegated grants");
        }
        if (service && scopes.stream().anyMatch(TokenContext::wildcard)) {
            throw new IllegalArgumentException("service token must not contain wildcard scope");
        }
    }

    public Map<String, Object> delegatedActorClaim() {
        if (grantType != GrantType.DELEGATED_SERVICE) return Map.of();
        return Map.of(
                TokenClaimContract.SUBJECT, actorSubjectId,
                TokenClaimContract.AUTHORIZATION_VERSION,
                subjectAuthorizationVersion,
                TokenClaimContract.TENANT_AUTHORIZATION_VERSION,
                tenantAuthorizationVersion
        );
    }

    private static boolean wildcard(String value) {
        return value != null && ("*".equals(value.trim())
                || "SCOPE_*".equalsIgnoreCase(value.trim()));
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
}
