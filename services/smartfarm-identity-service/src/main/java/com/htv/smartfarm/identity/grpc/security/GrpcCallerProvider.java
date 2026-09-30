package com.htv.smartfarm.identity.grpc.security;

import com.htv.smartfarm.security.core.SecurityIdentity;
import com.htv.smartfarm.security.grpc.GrpcSecurityContext;

import org.springframework.stereotype.Component;

@Component
public class GrpcCallerProvider {

    public GrpcCaller currentCaller() {
        SecurityIdentity identity = GrpcSecurityContext.requireIdentity();
        return new GrpcCaller(
                identity.subject(),
                identity.clientId(),
                identity.tenantId(),
                identity.authorities(),
                identity.audiences(),
                identity.tokenType(),
                GrpcSecurityContext.correlationId()
        );
    }
}
