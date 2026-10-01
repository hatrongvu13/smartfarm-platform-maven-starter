package com.htv.smartfarm.security.jwt;

import java.util.LinkedHashSet;
import java.util.Set;

import com.htv.smartfarm.security.TokenVerifier;
import com.htv.smartfarm.security.core.SecurityIdentity;
import com.htv.smartfarm.security.core.TokenType;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

public final class JwtTokenVerifier implements TokenVerifier {

    private final JwtDecoder decoder;

    public JwtTokenVerifier(JwtDecoder decoder) {
        this.decoder = decoder;
    }

    @Override
    public SecurityIdentity verify(String token) {
        Jwt jwt = decoder.decode(token);

        String clientId = firstNonBlank(
                jwt.getClaimAsString("client_id"),
                jwt.getClaimAsString("azp")
        );

        TokenType tokenType = resolveTokenType(jwt, clientId);

        return new SecurityIdentity(
                jwt.getSubject(),
                clientId,
                jwt.getClaimAsString("tenant_id"),
                JwtAuthorities.authorities(jwt),
                audienceSet(jwt),
                tokenType
        );
    }

    private TokenType resolveTokenType(
            Jwt jwt,
            String clientId
    ) {
        TokenType explicit = TokenType.fromClaim(
                jwt.getClaimAsString("token_type")
        );

        if (explicit != TokenType.UNKNOWN) {
            return explicit;
        }

        /*
         * Transitional inference only. New token issuers must emit token_type.
         * A client_id or azp claim strongly indicates a service token in the
         * current SmartFarm contract. Tokens without either remain UNKNOWN.
         */
        return clientId == null
                ? TokenType.UNKNOWN
                : TokenType.SERVICE;
    }

    private Set<String> audienceSet(Jwt jwt) {
        if (jwt.getAudience() == null) {
            return Set.of();
        }

        return Set.copyOf(
                new LinkedHashSet<>(jwt.getAudience())
        );
    }

    private String firstNonBlank(
            String first,
            String second
    ) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }

        return second == null || second.isBlank()
                ? null
                : second.trim();
    }
}
