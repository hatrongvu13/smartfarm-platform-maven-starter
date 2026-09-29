package com.htv.smartfarm.common.outbox;

/**
 * Logical state of an outbox message. This is not a JPA entity.
 */
public enum OutboxStatus {
    NEW,
    PUBLISHING,
    PUBLISHED,
    FAILED,
    DEAD;

    public boolean terminal() {
        return this == PUBLISHED || this == DEAD;
    }

    public boolean retryable() {
        return this == NEW || this == FAILED;
    }
}
