package com.htv.smartfarm.identity.grpc.security;

import java.util.Set;

public record GrpcCaller(
        String subjectId,
        String tenantId,
        Set<String> authorities,
        String correlationId
) {

    public GrpcCaller {
        authorities = authorities == null
                ? Set.of()
                : Set.copyOf(authorities);
    }

    public boolean hasAuthority(String authority) {
        return authority != null
                && authorities.contains(authority);
    }

    public boolean isSuperAdmin() {
        return authorities.contains("SCOPE_*");
    }

    public boolean hasAuthorityOrSuperAdmin(
            String authority
    ) {
        return hasAuthority(authority)
                || isSuperAdmin();
    }
}