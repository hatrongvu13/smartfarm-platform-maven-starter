package com.htv.smartfarm.identity.mfa.domain;

public enum MfaChallengePurpose {
    LOGIN,
    STEP_UP,
    MFA_DISABLE,
    PASSWORD_CHANGE,
    EMAIL_LOGIN_APPROVAL,
    TOTP_ENROLLMENT
}
