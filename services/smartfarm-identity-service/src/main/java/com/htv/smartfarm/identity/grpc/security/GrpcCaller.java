package com.htv.smartfarm.identity.grpc.security;

import java.util.Set;

import com.htv.smartfarm.security.core.TokenType;

public record GrpcCaller(
        String subjectId,
        String clientId,
        String tenantId,
        Set<String> authorities,
        Set<String> audiences,
        TokenType tokenType,
        String correlationId
) {
    public GrpcCaller {
        authorities = authorities == null ? Set.of() : Set.copyOf(authorities);
        audiences = audiences == null ? Set.of() : Set.copyOf(audiences);
        tokenType = tokenType == null ? TokenType.UNKNOWN : tokenType;
    }

    public boolean hasAuthority(String authority) {
        return authority != null && authorities.contains(authority);
    }

    public boolean isSuperAdmin() {
        return authorities.contains("SCOPE_*");
    }

    public boolean hasAuthorityOrSuperAdmin(String authority) {
        return hasAuthority(authority) || isSuperAdmin();
    }

    public boolean isUser() { return tokenType == TokenType.USER; }
    public boolean isService() { return tokenType == TokenType.SERVICE; }
}
