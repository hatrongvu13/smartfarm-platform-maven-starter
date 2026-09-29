package com.htv.smartfarm.security.grpc;

import io.grpc.Context;
import io.grpc.Status;

import java.util.Set;

public final class GrpcSecurityContext {

    private GrpcSecurityContext() {
    }

    public static final Context.Key<String> SUBJECT =
            Context.key("smartfarm-subject");

    public static final Context.Key<String> TENANT =
            Context.key("smartfarm-tenant");

    public static final Context.Key<String> CORRELATION_ID =
            Context.key("smartfarm-correlation-id");

    public static final Context.Key<Set<String>> AUTHORITIES =
            Context.key("smartfarm-authorities");

    public static String requireSubject() {
        String subject = SUBJECT.get();

        if (subject == null || subject.isBlank()) {
            throw Status.UNAUTHENTICATED
                    .withDescription(
                            "Authenticated gRPC subject is missing"
                    )
                    .asRuntimeException();
        }

        return subject;
    }

    public static String requireTenant() {
        String tenant = TENANT.get();

        if (tenant == null || tenant.isBlank()) {
            throw Status.UNAUTHENTICATED
                    .withDescription(
                            "Authenticated gRPC tenant is missing"
                    )
                    .asRuntimeException();
        }

        return tenant;
    }

    public static String correlationId() {
        return CORRELATION_ID.get();
    }

    public static Set<String> authorities() {
        Set<String> authorities = AUTHORITIES.get();

        return authorities == null
                ? Set.of()
                : authorities;
    }

    public static boolean hasAuthority(String authority) {
        return authority != null
                && authorities().contains(authority);
    }

    public static boolean isSuperAdmin() {
        return authorities().contains("SCOPE_*");
    }
}