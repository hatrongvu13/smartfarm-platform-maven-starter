package com.htv.smartfarm.order.application;

public class OrderVersionConflictException extends RuntimeException {

    private final long expectedVersion;
    private final long actualVersion;

    public OrderVersionConflictException(long expectedVersion, long actualVersion) {
        super("order version conflict: expected=" + expectedVersion + ", actual=" + actualVersion);
        this.expectedVersion = expectedVersion;
        this.actualVersion = actualVersion;
    }

    public long expectedVersion() { return expectedVersion; }
    public long actualVersion() { return actualVersion; }
}
