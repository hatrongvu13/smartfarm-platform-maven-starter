package com.htv.smartfarm.identity.oauth2;

import java.security.Principal;
import java.time.Instant;
import java.util.List;

public record IdentityAuthorizationPrincipal(
        String subjectId,
        String tenantId,
        List<String> authenticationMethods,
        Instant authenticatedAt
) implements Principal {
    public IdentityAuthorizationPrincipal {
        if (subjectId == null || subjectId.isBlank()
                || tenantId == null || tenantId.isBlank()
                || authenticatedAt == null) {
            throw new IllegalArgumentException(
                    "Authorization principal requires subject, tenant and authentication time"
            );
        }
        authenticationMethods = authenticationMethods == null
                ? List.of()
                : List.copyOf(authenticationMethods);
    }

    @Override
    public String getName() {
        return subjectId;
    }
}
