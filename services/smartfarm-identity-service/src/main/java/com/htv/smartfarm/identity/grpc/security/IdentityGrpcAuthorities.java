package com.htv.smartfarm.identity.grpc.security;

public final class IdentityGrpcAuthorities {

    private IdentityGrpcAuthorities() { }

    public static final String PRINCIPAL_READ =
            "SCOPE_identity:principal:read";
    public static final String PRINCIPAL_UPDATE =
            "SCOPE_identity:principal:update";
    public static final String PERMISSION_CHECK =
            "SCOPE_identity:permission:check";
    public static final String PRINCIPAL_IMPERSONATE =
            "SCOPE_identity:principal:impersonate";
    public static final String CROSS_TENANT =
            "SCOPE_identity:tenant:cross";
    public static final String USER_READ =
            "SCOPE_identity:user:read";
    public static final String USER_CREATE =
            "SCOPE_identity:user:create";
    public static final String USER_DISABLE =
            "SCOPE_identity:user:disable";
    public static final String ROLE_READ =
            "SCOPE_identity:role:read";
    public static final String ROLE_ASSIGN =
            "SCOPE_identity:role:assign";
    public static final String PERMISSION_READ =
            "SCOPE_identity:permission:read";
    public static final String PLATFORM_ROLE_MANAGE =
            "SCOPE_identity:platform:manage";
    public static final String PLATFORM_PERMISSION_MANAGE =
            "SCOPE_identity:platform:manage";
    public static final String SECURITY_READ =
            "SCOPE_identity:security:read";
    public static final String MFA_ENROLL =
            "SCOPE_identity:mfa:enroll";
    public static final String MFA_DISABLE =
            "SCOPE_identity:mfa:disable";
    public static final String MFA_RECOVERY_REGENERATE =
            "SCOPE_identity:mfa:recovery:regenerate";
    public static final String USER_MFA_RESET =
            "SCOPE_identity:user:mfa:reset";
    public static final String USER_CREDENTIAL_RESET =
            "SCOPE_identity:user:credential:reset";
    public static final String USER_PROFILE_UPDATE =
            "SCOPE_identity:user:profile:update";
    public static final String USER_PROFILE_MANAGE =
            "SCOPE_identity:user:profile:manage";
    public static final String USER_ACCOUNT_MANAGE =
            "SCOPE_identity:user:account:manage";

    public static final String ROLE_MANAGE =
            "SCOPE_identity:role:manage";

    public static final String PERMISSION_MANAGE =
            "SCOPE_identity:permission:manage";
}
