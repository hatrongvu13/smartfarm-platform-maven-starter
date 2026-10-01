package com.htv.smartfarm.security.grpc;

import java.util.Set;

import com.htv.smartfarm.security.core.SecurityIdentity;

import io.grpc.Context;
import io.grpc.Status;

public final class GrpcSecurityContext {

    private GrpcSecurityContext() {
    }

    public static final Context.Key<SecurityIdentity> IDENTITY =
            Context.key("smartfarm-security-identity");

    public static final Context.Key<String> CORRELATION_ID =
            Context.key("smartfarm-correlation-id");

    /**
     * @deprecated use IDENTITY and requireIdentity().
     */
    @Deprecated(forRemoval = true)
    public static final Context.Key<String> SUBJECT =
            Context.key("smartfarm-subject");

    /**
     * @deprecated use IDENTITY and requireIdentity().
     */
    @Deprecated(forRemoval = true)
    public static final Context.Key<String> TENANT =
            Context.key("smartfarm-tenant");

    /**
     * @deprecated use IDENTITY and requireIdentity().
     */
    @Deprecated(forRemoval = true)
    public static final Context.Key<Set<String>> AUTHORITIES =
            Context.key("smartfarm-authorities");

    public static SecurityIdentity requireIdentity() {
        SecurityIdentity identity = IDENTITY.get();

        if (identity == null) {
            throw Status.UNAUTHENTICATED
                    .withDescription(
                            "Authenticated gRPC identity is missing"
                    )
                    .asRuntimeException();
        }

        return identity;
    }

    public static String requireSubject() {
        return requireIdentity().subject();
    }

    public static String requireTenant() {
        return requireIdentity().tenantId();
    }

    public static String correlationId() {
        return CORRELATION_ID.get();
    }

    public static Set<String> authorities() {
        SecurityIdentity identity = IDENTITY.get();
        return identity == null
                ? Set.of()
                : identity.authorities();
    }

    public static boolean hasAuthority(String authority) {
        SecurityIdentity identity = IDENTITY.get();
        return identity != null
                && identity.hasAuthority(authority);
    }

    public static boolean isSuperAdmin() {
        SecurityIdentity identity = IDENTITY.get();
        return identity != null && identity.isSuperAdmin();
    }
}
