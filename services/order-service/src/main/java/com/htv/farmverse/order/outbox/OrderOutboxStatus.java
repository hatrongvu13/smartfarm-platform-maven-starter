package com.htv.farmverse.order.outbox;

public enum OrderOutboxStatus {
    NEW,
    PUBLISHING,
    PUBLISHED,
    FAILED,
    DEAD;

    public boolean claimable() {
        return this == NEW || this == FAILED;
    }
}
