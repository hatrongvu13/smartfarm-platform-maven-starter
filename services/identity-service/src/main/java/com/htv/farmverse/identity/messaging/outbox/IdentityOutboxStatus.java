package com.htv.farmverse.identity.messaging.outbox;

public enum IdentityOutboxStatus {
    NEW,
    PUBLISHING,
    PUBLISHED,
    FAILED,
    DEAD;

    public boolean retryable() {
        return this == NEW || this == FAILED;
    }
}
