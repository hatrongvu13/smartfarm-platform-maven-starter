package com.htv.smartfarm.identity.mfa.application.model;

import java.time.Instant;

public record TotpEnrollment(
        String authenticatorId,
        String otpauthUri,
        Instant expiresAt
) {
}
