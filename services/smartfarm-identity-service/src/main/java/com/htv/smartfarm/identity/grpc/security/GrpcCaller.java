package com.htv.smartfarm.identity.grpc;

import java.util.Set;

public record GrpcCaller(
        String subjectId,
        String tenantId,
        Set<String> authorities,
        boolean serviceAccount
) {

    public GrpcCaller {
        authorities = authorities == null
                ? Set.of()
                : Set.copyOf(authorities);
    }

    public boolean hasAuthority(String authority) {
        return authorities.contains(authority);
    }
}