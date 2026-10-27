package com.htv.farmverse.identity.token.grant;

import java.time.Instant;

public record IssuedToken(
        String value,
        String tokenType,
        String audience,
        Instant expiresAt
) {
}
