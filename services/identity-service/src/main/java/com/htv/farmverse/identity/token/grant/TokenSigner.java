package com.htv.farmverse.identity.token.grant;

public interface TokenSigner {
    IssuedToken sign(TokenContext context);
}
