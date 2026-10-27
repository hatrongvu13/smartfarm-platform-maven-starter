package com.htv.farmverse.identity.mfa.application.model;

import java.time.Instant;

public record MfaChallenge(
        String challengeToken,
        Instant expiresAt
) {
}
