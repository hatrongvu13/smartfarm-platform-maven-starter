package com.htv.smartfarm.identity.token.grant;

public interface RefreshSessionPort {
    RefreshGrant create(TokenContext context);
    RefreshSessionDescriptor resolve(String rawRefreshToken);
    RefreshGrant rotate(String rawRefreshToken);
    void revokeFamily(String familyId, String reason);
    void revokeByRawToken(String rawRefreshToken, String reason);

    record RefreshGrant(String rawToken, String sessionId, String familyId) { }
}
