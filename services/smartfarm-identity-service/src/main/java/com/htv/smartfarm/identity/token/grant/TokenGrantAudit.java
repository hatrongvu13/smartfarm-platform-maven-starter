package com.htv.smartfarm.identity.token.grant;

public interface TokenGrantAudit {
    void granted(TokenContext context, IssuedToken token);
    void denied(GrantType grantType, String reasonCode);
}
