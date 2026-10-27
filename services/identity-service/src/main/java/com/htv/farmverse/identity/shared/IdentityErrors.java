package com.htv.farmverse.identity.shared;

/** Mã lỗi {@code SF-IDENTITY-xxx} trả trong field {@code code} của ProblemDetail. */
public final class IdentityErrors {

    public static final String ACCOUNT_NOT_FOUND = "SF-IDENTITY-001";
    public static final String EMAIL_TAKEN = "SF-IDENTITY-002";
    public static final String WRONG_PASSWORD = "SF-IDENTITY-003";
    public static final String MEMBERSHIP_NOT_FOUND = "SF-IDENTITY-004";
    public static final String ALREADY_MEMBER = "SF-IDENTITY-005";
    public static final String ROLE_NOT_FOUND = "SF-IDENTITY-006";
    public static final String ROLE_EXISTS = "SF-IDENTITY-007";
    public static final String SYSTEM_ROLE_LOCKED = "SF-IDENTITY-008";
    public static final String PERMISSION_NOT_FOUND = "SF-IDENTITY-009";
    public static final String PERMISSION_EXISTS = "SF-IDENTITY-010";
    public static final String SYSTEM_PERMISSION_LOCKED = "SF-IDENTITY-011";
    public static final String PERMISSION_IN_USE = "SF-IDENTITY-012";
    public static final String SELF_LOCKOUT = "SF-IDENTITY-013";
    public static final String TENANT_EXISTS = "SF-IDENTITY-014";
    public static final String TENANT_REQUIRED = "SF-IDENTITY-015";

    private IdentityErrors() {
    }
}
