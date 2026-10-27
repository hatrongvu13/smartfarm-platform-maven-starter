package com.htv.farmverse.identity.authorization.version;

public record AuthorizationVersionTuple(
        long tenantVersion,
        long subjectVersion
) {
    public AuthorizationVersionTuple {
        if (tenantVersion < 0 || subjectVersion < 0) {
            throw new IllegalArgumentException("authorization versions must not be negative");
        }
    }
}
