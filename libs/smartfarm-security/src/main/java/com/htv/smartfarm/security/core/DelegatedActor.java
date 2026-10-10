package com.htv.smartfarm.security.core;

/** Signed human actor carried by a delegated service token. */
public record DelegatedActor(
        String subject,
        long authorizationVersion,
        long tenantAuthorizationVersion
) {
    public DelegatedActor {
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("delegated actor subject must not be blank");
        }
        subject = subject.trim();
        if (authorizationVersion < 0 || tenantAuthorizationVersion < 0) {
            throw new IllegalArgumentException("delegated actor versions must not be negative");
        }
    }
}
