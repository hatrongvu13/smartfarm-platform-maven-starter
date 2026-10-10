package com.htv.smartfarm.security.jwt;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import com.htv.smartfarm.security.TokenVerifier;
import com.htv.smartfarm.security.core.DelegatedActor;
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
        DelegatedActor actor = delegatedActor(jwt, tokenType);

        return new SecurityIdentity(
                jwt.getSubject(),
                clientId,
                jwt.getClaimAsString("tenant_id"),
                JwtAuthorities.authorities(jwt),
                audienceSet(jwt),
                tokenType,
                longClaim(jwt, "authorization_version", false),
                longClaim(jwt, "tenant_authorization_version", false),
                actor
        );
    }

    private DelegatedActor delegatedActor(Jwt jwt, TokenType tokenType) {
        Object raw = jwt.getClaims().get("act");
        if (raw == null) return null;
        if (tokenType != TokenType.SERVICE) {
            throw new IllegalArgumentException("act claim is allowed only on service tokens");
        }
        if (!(raw instanceof Map<?, ?> values)) {
            throw new IllegalArgumentException("act claim must be an object");
        }
        Object subject = values.get("sub");
        if (!(subject instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("act.sub must not be blank");
        }
        return new DelegatedActor(
                text,
                number(values.get("authorization_version"), "act.authorization_version"),
                number(values.get("tenant_authorization_version"), "act.tenant_authorization_version")
        );
    }

    private Long longClaim(Jwt jwt, String name, boolean required) {
        Object value = jwt.getClaims().get(name);
        if (value == null && !required) return null;
        return number(value, name);
    }

    private long number(Object value, String name) {
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(name + " must be a number");
        }
        long result = number.longValue();
        if (result < 0) throw new IllegalArgumentException(name + " must not be negative");
        return result;
    }

    private TokenType resolveTokenType(Jwt jwt, String clientId) {
        TokenType explicit = TokenType.fromClaim(jwt.getClaimAsString("token_type"));
        if (explicit != TokenType.UNKNOWN) return explicit;
        return clientId == null ? TokenType.UNKNOWN : TokenType.SERVICE;
    }

    private Set<String> audienceSet(Jwt jwt) {
        if (jwt.getAudience() == null) return Set.of();
        return Set.copyOf(new LinkedHashSet<>(jwt.getAudience()));
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) return first.trim();
        return second == null || second.isBlank() ? null : second.trim();
    }
}
