package com.htv.farmverse.order.saga.persistence;

public enum OrderSagaFailureAction {
    RETRY_SCHEDULED,
    COMPENSATION_STARTED,
    MANUAL_REVIEW
}
