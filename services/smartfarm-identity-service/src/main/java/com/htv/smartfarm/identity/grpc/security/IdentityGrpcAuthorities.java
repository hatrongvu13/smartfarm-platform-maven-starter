package com.htv.smartfarm.identity.grpc.security;

public final class IdentityGrpcAuthorities {

    private IdentityGrpcAuthorities() {
    }

    public static final String PRINCIPAL_READ =
            "SCOPE_identity.principal.read";

    public static final String PRINCIPAL_UPDATE =
            "SCOPE_identity.principal.update";

    public static final String PERMISSION_CHECK =
            "SCOPE_identity.permission.check";

    public static final String USER_READ =
            "SCOPE_identity.user.read";

    public static final String USER_CREATE =
            "SCOPE_identity.user.create";

    public static final String USER_DISABLE =
            "SCOPE_identity.user.disable";

    public static final String ROLE_READ =
            "SCOPE_identity.role.read";

    public static final String ROLE_ASSIGN =
            "SCOPE_identity.role.assign";

    public static final String PERMISSION_READ =
            "SCOPE_identity.permission.read";

    public static final String PLATFORM_ROLE_MANAGE =
            "SCOPE_identity.platform.role.manage";

    public static final String PLATFORM_PERMISSION_MANAGE =
            "SCOPE_identity.platform.permission.manage";

    public static final String PRINCIPAL_IMPERSONATE =
            "SCOPE_identity.principal.impersonate";

    public static final String CROSS_TENANT =
            "SCOPE_identity.tenant.cross";
}