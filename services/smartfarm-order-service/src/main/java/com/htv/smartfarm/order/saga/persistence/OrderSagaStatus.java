package com.htv.smartfarm.order.saga.persistence;

public enum OrderSagaStatus {
    PENDING, RUNNING, COMPENSATING, COMPLETED, COMPENSATED, FAILED, MANUAL_REVIEW;
    public boolean terminal() {
        return this == COMPLETED || this == COMPENSATED || this == FAILED || this == MANUAL_REVIEW;
    }
    public boolean claimable() {
        return this == PENDING || this == RUNNING || this == COMPENSATING;
    }
}
