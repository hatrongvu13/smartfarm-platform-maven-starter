package com.htv.smartfarm.order.saga.persistence;

public enum OrderSagaStepType {
    RESERVE_STOCK, POST_FINANCE, COMMIT_STOCK, REVERSE_FINANCE, RELEASE_STOCK;
    public boolean compensation() { return this == REVERSE_FINANCE || this == RELEASE_STOCK; }
}
