package com.htv.farmverse.identity.web;

/**
 * Biểu thức {@code @PreAuthorize} dùng chung. Super admin luôn qua.
 * Mã permission khớp danh mục trong {@code application.yml} (farmverse.identity.catalog).
 */
final class Can {

    private static final String OR_SUPER_ADMIN = " or hasRole('SUPER_ADMIN')";

    static final String READ_USERS = "hasAuthority('identity:user:read')" + OR_SUPER_ADMIN;
    static final String MANAGE_USERS = "hasAuthority('identity:user:manage')" + OR_SUPER_ADMIN;
    static final String READ_ROLES = "hasAuthority('identity:role:read')" + OR_SUPER_ADMIN;
    static final String MANAGE_ROLES = "hasAuthority('identity:role:manage')" + OR_SUPER_ADMIN;
    static final String MANAGE_PERMISSIONS = "hasAuthority('identity:permission:manage')" + OR_SUPER_ADMIN;
    static final String MANAGE_TENANTS = "hasAuthority('identity:tenant:manage')" + OR_SUPER_ADMIN;

    private Can() {
    }
}
