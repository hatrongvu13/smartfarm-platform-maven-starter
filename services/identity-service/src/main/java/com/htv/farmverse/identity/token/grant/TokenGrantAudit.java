package com.htv.farmverse.identity.token.grant;

public interface TokenGrantAudit {
    void granted(TokenContext context, IssuedToken token);
    void denied(GrantType grantType, String reasonCode);
}
