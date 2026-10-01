package com.htv.smartfarm.identity.mfa.domain;

public enum MfaChallengeStatus {
    PENDING,
    VERIFIED,
    CONSUMED,
    EXPIRED,
    LOCKED
}
