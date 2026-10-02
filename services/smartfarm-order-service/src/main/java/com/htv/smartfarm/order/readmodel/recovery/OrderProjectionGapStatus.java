package com.htv.smartfarm.order.readmodel.recovery;

public enum OrderProjectionGapStatus {
    OPEN,
    RECOVERING,
    RETRY_WAIT,
    RESOLVED,
    MANUAL_REVIEW;

    public boolean claimable() {
        return this == OPEN || this == RETRY_WAIT;
    }

    public boolean terminal() {
        return this == RESOLVED || this == MANUAL_REVIEW;
    }
}
