package com.htv.smartfarm.identity.grpc.security;

/**
 * Authority constants used by Identity gRPC services.
 */
public final class IdentityGrpcAuthorities {

    private static final String SCOPE_PREFIX =
            "SCOPE_identity:";

    private IdentityGrpcAuthorities() {
        throw new UnsupportedOperationException(
                "Utility class must not be instantiated"
        );
    }

    // Principal
    public static final String PRINCIPAL_READ =
            SCOPE_PREFIX + "principal:read";

    public static final String PRINCIPAL_UPDATE =
            SCOPE_PREFIX + "principal:update";

    public static final String PRINCIPAL_IMPERSONATE =
            SCOPE_PREFIX + "principal:impersonate";

    // Permission evaluation
    public static final String PERMISSION_CHECK =
            SCOPE_PREFIX + "permission:check";

    // Tenant access
    public static final String CROSS_TENANT =
            SCOPE_PREFIX + "tenant:cross";

    // Tenant users
    public static final String USER_READ =
            SCOPE_PREFIX + "user:read";

    public static final String USER_CREATE =
            SCOPE_PREFIX + "user:create";

    public static final String USER_DISABLE =
            SCOPE_PREFIX + "user:disable";

    public static final String USER_PROFILE_UPDATE =
            SCOPE_PREFIX + "user:profile:update";

    public static final String USER_PROFILE_MANAGE =
            SCOPE_PREFIX + "user:profile:manage";

    public static final String USER_ACCOUNT_MANAGE =
            SCOPE_PREFIX + "user:account:manage";

    // Tenant roles
    public static final String ROLE_READ =
            SCOPE_PREFIX + "role:read";

    public static final String ROLE_ASSIGN =
            SCOPE_PREFIX + "role:assign";

    public static final String ROLE_MANAGE =
            SCOPE_PREFIX + "role:manage";

    // Tenant permissions
    public static final String PERMISSION_READ =
            SCOPE_PREFIX + "permission:read";

    public static final String PERMISSION_MANAGE =
            SCOPE_PREFIX + "permission:manage";

    // Security and credentials
    public static final String SECURITY_READ =
            SCOPE_PREFIX + "security:read";

    public static final String USER_CREDENTIAL_RESET =
            SCOPE_PREFIX + "user:credential:reset";

    // MFA
    public static final String MFA_ENROLL =
            SCOPE_PREFIX + "mfa:enroll";

    public static final String MFA_DISABLE =
            SCOPE_PREFIX + "mfa:disable";

    public static final String MFA_RECOVERY_REGENERATE =
            SCOPE_PREFIX + "mfa:recovery:regenerate";

    public static final String USER_MFA_RESET =
            SCOPE_PREFIX + "user:mfa:reset";

    // Internal token and session administration
    public static final String TOKEN_ISSUE =
            SCOPE_PREFIX + "token:issue";

    public static final String SESSION_READ =
            SCOPE_PREFIX + "session:read";

    public static final String SESSION_REVOKE =
            SCOPE_PREFIX + "session:revoke";

    // Platform administration
    public static final String PLATFORM_ROLE_MANAGE =
            SCOPE_PREFIX + "platform:manage";

    /**
     * Kept as a separate constant for semantic clarity and backward
     * compatibility. It currently maps to the same platform management scope.
     */
    public static final String PLATFORM_PERMISSION_MANAGE =
            SCOPE_PREFIX + "platform:manage";
}