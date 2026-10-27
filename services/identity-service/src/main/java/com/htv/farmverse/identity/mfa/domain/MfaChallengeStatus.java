package com.htv.farmverse.identity.mfa.domain;

public enum MfaChallengeStatus {
    PENDING,
    VERIFIED,
    CONSUMED,
    EXPIRED,
    LOCKED
}
