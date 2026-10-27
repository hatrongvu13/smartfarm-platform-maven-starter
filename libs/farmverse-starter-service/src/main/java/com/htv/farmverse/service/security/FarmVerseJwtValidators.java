package com.htv.farmverse.service.security;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;

/** Bộ kiểm token chuẩn cho resource server: chữ ký + thời hạn + issuer (mặc định của Spring) và audience. */
public final class FarmVerseJwtValidators {

    private FarmVerseJwtValidators() {
    }

    public static OAuth2TokenValidator<Jwt> issuerAndAudience(String issuer, String audience) {
        OAuth2TokenValidator<Jwt> audienceValidator = new JwtClaimValidator<java.util.List<String>>(
                "aud", aud -> aud != null && aud.contains(audience));
        return new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer), audienceValidator);
    }
}
