package com.htv.smartfarm.identity.token.grant;

public interface TokenSigner {
    IssuedToken sign(TokenContext context);
}
