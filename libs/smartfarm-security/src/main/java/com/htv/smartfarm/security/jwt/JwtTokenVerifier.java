package com.htv.smartfarm.security.jwt;

import com.htv.smartfarm.security.TokenVerifier;

import java.util.Set;

import org.springframework.security.oauth2.jwt.JwtDecoder;

public final class JwtTokenVerifier implements TokenVerifier {
    private final JwtDecoder decoder;

    public JwtTokenVerifier(JwtDecoder decoder) {
        this.decoder = decoder;
    }

    @Override
    public VerifiedToken verify(String token) {
        var jwt = decoder.decode(token);
        return new VerifiedToken(jwt.getSubject(), JwtAuthorities.authorities(jwt), jwt.getClaimAsString("tenant_id"));
    }
}
