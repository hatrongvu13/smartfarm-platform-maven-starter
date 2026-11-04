package com.htv.farmverse.service.security;

/**
 * Tên claim trong access token do identity-service cấp. Mọi service đọc claim qua các hằng này,
 * không tự gõ chuỗi.
 */
public final class FarmVerseClaims {

    /**
     * Audience riêng của từng resource server (ADR-0006). Mỗi service chỉ nhận token có audience của mình,
     * để token đọc được ở một service không tự động dùng được ở service khác ngoài danh sách {@code aud}.
     */
    public static final String FARM_AUDIENCE = "farm-service";
    public static final String IDENTITY_AUDIENCE = "identity-service";

    /**
     * Audience chung cũ. Không còn được cấp; chỉ còn để một resource server tạm nhận trong lúc chuyển đổi
     * (cấu hình danh sách audience được nhận), rồi bỏ.
     */
    public static final String LEGACY_API_AUDIENCE = "farmverse-api";

    /** Scope mà gateway xin cho mọi lời gọi API; resource server đòi scope này ({@code SCOPE_farmverse.api}). */
    public static final String API_SCOPE = "farmverse.api";

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
