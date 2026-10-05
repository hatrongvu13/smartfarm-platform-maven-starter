package com.htv.smartfarm.security.issuer;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;

import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * RSA JWT issuer for the trusted Identity authorization service only.
 *
 * <p>Never instantiate this class in Gateway or business services.
 */
public final class RsaJwtIssuer {

    public static final String TOKEN_TYPE_USER = "user";
    public static final String TOKEN_TYPE_SERVICE = "service";

    private static final Duration MAXIMUM_LIFETIME =
            Duration.ofMinutes(15);

    private final JwtEncoder encoder;
    private final RSAKey key;
    private final String issuer;
    private final Duration lifetime;
    private final Clock clock;

    public RsaJwtIssuer(
            RSAPublicKey publicKey,
            RSAPrivateKey privateKey,
            String keyId,
            String issuer,
            Duration lifetime
    ) {
        this(
                publicKey,
                privateKey,
                keyId,
                issuer,
                lifetime,
                Clock.systemUTC()
        );
    }

    RsaJwtIssuer(
            RSAPublicKey publicKey,
            RSAPrivateKey privateKey,
            String keyId,
            String issuer,
            Duration lifetime,
            Clock clock
    ) {
        Objects.requireNonNull(publicKey, "publicKey must not be null");
        Objects.requireNonNull(privateKey, "privateKey must not be null");
        this.clock = Objects.requireNonNull(
                clock,
                "clock must not be null"
        );

        this.issuer = requireText(issuer, "issuer");
        this.lifetime = validateLifetime(lifetime);
        String normalizedKeyId = requireText(keyId, "keyId");

        this.key = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(normalizedKeyId)
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .build();

        this.encoder = new NimbusJwtEncoder(
                new ImmutableJWKSet<SecurityContext>(
                        new JWKSet(key)
                )
        );
    }

    public String issueUserToken(
            String subject,
            String tenantId,
            String audience,
            Collection<String> scopes,
            Collection<String> roles
    ) {
        JwtClaimsSet claims = baseClaims(
                subject,
                tenantId,
                audience,
                scopes
        )
                .claim("token_type", TOKEN_TYPE_USER)
                .claim("roles", normalizeValues(roles))
                .build();

        return encode(claims);
    }

    public String issueServiceToken(
            String clientId,
            String tenantId,
            String audience,
            Collection<String> scopes
    ) {
        String normalizedClientId = requireText(
                clientId,
                "clientId"
        );
        rejectWildcardServiceScope(scopes);

        JwtClaimsSet claims = baseClaims(
                normalizedClientId,
                tenantId,
                audience,
                scopes
        )
                .claim("token_type", TOKEN_TYPE_SERVICE)
                .claim("client_id", normalizedClientId)
                .claim("roles", List.of())
                .build();

        return encode(claims);
    }

    /**
     * Compatibility bridge for legacy user-token callers.
     *
     * @deprecated use issueUserToken or issueServiceToken explicitly.
     */
    @Deprecated(forRemoval = true)
    public String issue(
            String subject,
            String tenantId,
            String audience,
            Collection<String> scopes,
            Collection<String> roles
    ) {
        return issueUserToken(
                subject,
                tenantId,
                audience,
                scopes,
                roles
        );
    }

    public String publicJwksJson() {
        return new JWKSet(key.toPublicJWK()).toString();
    }

    private JwtClaimsSet.Builder baseClaims(
            String subject,
            String tenantId,
            String audience,
            Collection<String> scopes
    ) {
        String normalizedSubject = requireText(
                subject,
                "subject"
        );
        String normalizedTenantId = requireText(
                tenantId,
                "tenantId"
        );
        String normalizedAudience = requireText(
                audience,
                "audience"
        );
        Instant now = clock.instant();

        return JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(normalizedSubject)
                .audience(List.of(normalizedAudience))
                .issuedAt(now)
                .notBefore(now)
                .expiresAt(now.plus(lifetime))
                .id(UUID.randomUUID().toString())
                .claim("tenant_id", normalizedTenantId)
                .claim("scope", String.join(" ", normalizeValues(scopes)));
    }

    private String encode(JwtClaimsSet claims) {
        JwsHeader header = JwsHeader
                .with(SignatureAlgorithm.RS256)
                .keyId(key.getKeyID())
                .build();

        return encoder.encode(
                JwtEncoderParameters.from(header, claims)
        ).getTokenValue();
    }

    private static void rejectWildcardServiceScope(Collection<String> scopes) {
        if (scopes == null) return;
        for (String scope : scopes) {
            if (scope == null) continue;
            String normalized = scope.trim();
            if ("*".equals(normalized) || "SCOPE_*".equalsIgnoreCase(normalized))
                throw new IllegalArgumentException("service tokens must not contain wildcard scope");
        }
    }

    private static List<String> normalizeValues(
            Collection<String> values
    ) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }

        Set<String> normalized = new LinkedHashSet<>();

        for (String value : values) {
            if (value != null && !value.isBlank()) {
                normalized.add(value.trim());
            }
        }

        return List.copyOf(normalized);
    }

    private static Duration validateLifetime(Duration lifetime) {
        if (lifetime == null
                || lifetime.isZero()
                || lifetime.isNegative()
                || lifetime.compareTo(MAXIMUM_LIFETIME) > 0) {
            throw new IllegalArgumentException(
                    "lifetime must be between 1 second and 15 minutes"
            );
        }

        return lifetime;
    }

    private static String requireText(
            String value,
            String field
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    field + " must not be blank"
            );
        }

        return value.trim();
    }
}
