package com.htv.smartfarm.identity.token.grant;

import java.time.Instant;

public record IssuedToken(
        String value,
        String tokenType,
        String audience,
        Instant expiresAt
) {
}
