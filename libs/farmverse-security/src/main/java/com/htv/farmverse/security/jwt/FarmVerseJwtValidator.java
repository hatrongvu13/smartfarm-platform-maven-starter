package com.htv.farmverse.security.jwt;

import java.util.Set;

import com.htv.farmverse.common.contract.TokenClaimContract;
import com.htv.farmverse.security.config.FarmVerseSecurityProperties;
import com.htv.farmverse.security.core.TokenType;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;

public final class FarmVerseJwtValidator
        implements OAuth2TokenValidator<Jwt> {

    private final Set<String> audiences;
    private final OAuth2TokenValidator<Jwt> defaults;

    public FarmVerseJwtValidator(
            FarmVerseSecurityProperties.Jwt properties
    ) {
        this.audiences = properties.audiences();
        this.defaults = JwtValidators.createDefaultWithIssuer(
                properties.issuer().toString()
        );
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        OAuth2TokenValidatorResult standard = defaults.validate(jwt);

        if (standard.hasErrors()) {
            return standard;
        }

        boolean audienceMatched = jwt.getAudience()
                .stream()
                .anyMatch(audiences::contains);

        if (!audienceMatched) {
            return fail("audience_mismatch");
        }

        if (jwt.getSubject() == null
                || jwt.getSubject().isBlank()) {
            return fail("missing_subject");
        }

        if (jwt.getExpiresAt() == null) {
            return fail("missing_expiry");
        }

        TokenType tokenType = TokenType.fromClaim(
                jwt.getClaimAsString(TokenClaimContract.TOKEN_TYPE)
        );
        if (tokenType == TokenType.UNKNOWN) {
            return fail("missing_or_invalid_token_type");
        }

        String tenant = jwt.getClaimAsString(TokenClaimContract.TENANT_ID);

        if (tokenType == TokenType.USER && (tenant == null || tenant.isBlank())) {
            return fail("missing_tenant");
        }

        Object contractVersion = jwt.getClaims().get(
                TokenClaimContract.CONTRACT_VERSION_CLAIM
        );
        if (!TokenClaimContract.supportsVersion(contractVersion)) {
            return fail("unsupported_identity_claims_version");
        }

        return OAuth2TokenValidatorResult.success();
    }

    private static OAuth2TokenValidatorResult fail(String code) {
        return OAuth2TokenValidatorResult.failure(
                new OAuth2Error(
                        "invalid_token",
                        code,
                        null
                )
        );
    }
}
