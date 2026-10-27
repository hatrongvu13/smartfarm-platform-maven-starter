package com.htv.farmverse.identity.token.grant;

public record TokenGrant(
        IssuedToken accessToken,
        String refreshToken,
        String sessionId
) {
    public static TokenGrant accessOnly(IssuedToken token) {
        return new TokenGrant(token, null, null);
    }
}
