package com.htv.farmverse.security.grpc;

import java.util.Set;

import com.htv.farmverse.security.core.SecurityIdentity;

import io.grpc.Context;
import io.grpc.Status;

public final class GrpcSecurityContext {

    private GrpcSecurityContext() {
    }

    public static final Context.Key<SecurityIdentity> IDENTITY =
            Context.key("farmverse-security-identity");

    public static final Context.Key<String> CORRELATION_ID =
            Context.key("farmverse-correlation-id");

    /**
     * @deprecated use IDENTITY and requireIdentity().
     */
    @Deprecated(forRemoval = true)
    public static final Context.Key<String> SUBJECT =
            Context.key("farmverse-subject");

    /**
     * @deprecated use IDENTITY and requireIdentity().
     */
    @Deprecated(forRemoval = true)
    public static final Context.Key<String> TENANT =
            Context.key("farmverse-tenant");

    /**
     * @deprecated use IDENTITY and requireIdentity().
     */
    @Deprecated(forRemoval = true)
    public static final Context.Key<Set<String>> AUTHORITIES =
            Context.key("farmverse-authorities");

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

    /** Machine/user subject authenticated by the JWT itself. */
    public static String requireSubject() {
        return requireIdentity().subject();
    }

    public static String requireMachineSubject() {
        return requireIdentity().machineSubject();
    }

    public static String requireEffectiveActor() {
        return requireIdentity().effectiveActorSubject();
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
