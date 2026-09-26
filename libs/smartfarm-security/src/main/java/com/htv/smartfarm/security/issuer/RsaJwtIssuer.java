package com.htv.smartfarm.security.issuer;

import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;

import java.security.interfaces.*;
import java.time.*;
import java.util.*;

import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;

/**
 * Import/use ONLY in a dedicated trusted authorization service, never in gateway or business services.
 */
public final class RsaJwtIssuer {
    private final JwtEncoder encoder;
    private final RSAKey key;
    private final String issuer;
    private final Duration lifetime;

    public RsaJwtIssuer(RSAPublicKey publicKey, RSAPrivateKey privateKey, String kid, String issuer, Duration lifetime) {
        if (kid == null || kid.isBlank() || issuer == null || issuer.isBlank() || lifetime == null || lifetime.isNegative() || lifetime.isZero() || lifetime.compareTo(Duration.ofMinutes(15)) > 0)
            throw new IllegalArgumentException("kid/issuer required; lifetime must be between 1 second and 15 minutes");
        this.key = new RSAKey.Builder(publicKey).privateKey(privateKey).keyID(kid).keyUse(KeyUse.SIGNATURE).algorithm(com.nimbusds.jose.JWSAlgorithm.RS256).build();
        this.encoder = new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(key)));
        this.issuer = issuer;
        this.lifetime = lifetime;
    }

    public String issue(String subject, String tenantId, String audience, Collection<String> scopes, Collection<String> roles) {
        if (subject == null || subject.isBlank() || tenantId == null || tenantId.isBlank() || audience == null || audience.isBlank())
            throw new IllegalArgumentException("subject, tenant and audience required");
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(issuer).subject(subject).audience(List.of(audience))
                .issuedAt(now).notBefore(now).expiresAt(now.plus(lifetime)).id(UUID.randomUUID().toString())
                .claim("tenant_id", tenantId).claim("scope", String.join(" ", scopes)).claim("roles", List.copyOf(roles)).build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(key.getKeyID()).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /**
     * Public keys only; the private key must never be returned from a web endpoint.
     */
    public String publicJwksJson() {
        return new JWKSet(key.toPublicJWK()).toString();
    }
}
