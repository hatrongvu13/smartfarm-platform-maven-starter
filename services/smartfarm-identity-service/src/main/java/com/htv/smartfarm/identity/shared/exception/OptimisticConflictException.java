package com.htv.smartfarm.identity.shared.exception;

public class OptimisticConflictException extends RuntimeException {

    public OptimisticConflictException(
            long expectedVersion,
            long actualVersion
    ) {
        super(
                "Version conflict. Expected "
                        + expectedVersion
                        + " but found "
                        + actualVersion
        );
    }
}
