package com.htv.smartfarm.identity.grpc.security;

import com.htv.smartfarm.security.grpc.GrpcSecurityContext;

import io.grpc.Status;

import java.util.Set;

import org.springframework.stereotype.Component;

@Component
public class GrpcCallerProvider {

    public GrpcCaller currentCaller() {
        String subjectId =
                GrpcSecurityContext.SUBJECT.get();

        String tenantId =
                GrpcSecurityContext.TENANT.get();

        Set<String> authorities =
                GrpcSecurityContext.authorities();

        String correlationId =
                GrpcSecurityContext.CORRELATION_ID.get();

        if (!hasText(subjectId)) {
            throw Status.UNAUTHENTICATED
                    .withDescription(
                            "Authenticated gRPC subject is missing"
                    )
                    .asRuntimeException();
        }

        if (!hasText(tenantId)) {
            throw Status.UNAUTHENTICATED
                    .withDescription(
                            "Authenticated gRPC tenant is missing"
                    )
                    .asRuntimeException();
        }

        return new GrpcCaller(
                subjectId,
                tenantId,
                authorities,
                correlationId
        );
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}