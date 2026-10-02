package com.htv.smartfarm.order.saga.persistence;

public enum OrderSagaStepStatus {
    PENDING, PROCESSING, SUCCEEDED, FAILED, COMPENSATING, COMPENSATED, SKIPPED, MANUAL_REVIEW;
    public boolean terminal() {
        return this == SUCCEEDED || this == COMPENSATED || this == SKIPPED || this == MANUAL_REVIEW;
    }
}
