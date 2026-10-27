package com.htv.farmverse.identity.tenant;

public enum MembershipStatus {
    /** Được đăng nhập vào tenant. */
    ACTIVE,
    /** Tạm khóa, có thể bật lại. */
    SUSPENDED,
    /** Đã rời tenant. */
    DISABLED
}
