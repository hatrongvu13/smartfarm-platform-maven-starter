package com.htv.farmverse.service.security;

/**
 * Tên claim trong access token do identity-service cấp. Mọi service đọc claim qua các hằng này,
 * không tự gõ chuỗi.
 */
public final class FarmVerseClaims {

    /** Audience duy nhất của access token người dùng; mọi resource server kiểm giá trị này. */
    public static final String API_AUDIENCE = "farmverse-api";

    public static final String TENANT_ID = "tenant_id";
    public static final String MEMBERSHIP_ID = "membership_id";
    /** Mã permission hiệu lực trong tenant đang đăng nhập, ví dụ {@code livestock:animal:read}. */
    public static final String PERMISSIONS = "perms";
    /** Mã role trong tenant, ví dụ {@code ADMIN}, {@code SUPER_ADMIN}. */
    public static final String ROLES = "roles";
    /** {@code user} cho người dùng, {@code service} cho client_credentials. */
    public static final String TOKEN_TYPE = "token_type";

    public static final String SUPER_ADMIN_ROLE = "SUPER_ADMIN";

    private FarmVerseClaims() {
    }
}
