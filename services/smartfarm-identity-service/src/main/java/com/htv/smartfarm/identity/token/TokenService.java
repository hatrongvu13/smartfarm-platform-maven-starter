package com.htv.smartfarm.identity.token;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.htv.smartfarm.identity.config.IdentitySettings;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

@Component
public class TokenService {

    private static final Duration MAXIMUM_TOKEN_TTL = Duration.ofMinutes(15);

    private final IdentitySettings settings;
    private final RSAKey key;
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final Clock clock;

    public TokenService(
            IdentitySettings settings,
            ResourceLoader resourceLoader,
            Clock clock
    ) throws Exception {
        this.settings = settings;
        this.clock = clock;
        KeyFactory factory = KeyFactory.getInstance("RSA");
        RSAPrivateKey privateKey = (RSAPrivateKey) factory.generatePrivate(
                new PKCS8EncodedKeySpec(pem(
                        resourceLoader,
                        settings.privateKeyPath(),
                        "PRIVATE KEY"
                ))
        );
        RSAPublicKey publicKey = (RSAPublicKey) factory.generatePublic(
                new X509EncodedKeySpec(pem(
                        resourceLoader,
                        settings.publicKeyPath(),
                        "PUBLIC KEY"
                ))
        );
        if (publicKey.getModulus().bitLength() < 2048
                || !privateKey.getModulus().equals(publicKey.getModulus())) {
            throw new IllegalArgumentException(
                    "RSA key pair mismatch or key smaller than 2048 bits"
            );
        }
        key = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(settings.keyId())
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .build();
        encoder = new NimbusJwtEncoder(
                new ImmutableJWKSet<SecurityContext>(new JWKSet(key))
        );
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder
                .withPublicKey(publicKey)
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        jwtDecoder.setJwtValidator(jwt -> {
            var standard = JwtValidators
                    .createDefaultWithIssuer(settings.issuer())
                    .validate(jwt);
            if (standard.hasErrors()) return standard;
            boolean acceptedAudience = jwt.getAudience().contains(
                    settings.gatewayAudience()
            ) || jwt.getAudience().contains(settings.identityAudience());
            if (!acceptedAudience || jwt.getSubject() == null
                    || jwt.getSubject().isBlank()
                    || jwt.getExpiresAt() == null
                    || jwt.getClaimAsString("tenant_id") == null
                    || jwt.getClaimAsString("token_type") == null) {
                return OAuth2TokenValidatorResult.failure(
                        new OAuth2Error(
                                "invalid_token",
                                "invalid audience or identity claims",
                                null
                        )
                );
            }
            return OAuth2TokenValidatorResult.success();
        });
        decoder = jwtDecoder;
    }

    public JwtDecoder decoder() { return decoder; }

    public String jwks() {
        return new JWKSet(key.toPublicJWK()).toString();
    }

    public String issueUserToken(
            String subjectId,
            String tenantId,
            Collection<String> scopes,
            Collection<String> roles,
            Duration ttl
    ) {
        return issue(
                subjectId,
                null,
                tenantId,
                settings.gatewayAudience(),
                scopes,
                roles,
                "user",
                ttl
        );
    }

    public String issueServiceToken(
            String clientId,
            String tenantId,
            String audience,
            Collection<String> scopes,
            Duration ttl
    ) {
        String normalizedClientId = requireText(clientId, "clientId");
        validateServiceScopes(scopes);
        return issue(
                "svc:" + normalizedClientId,
                normalizedClientId,
                tenantId,
                audience,
                scopes,
                List.of(),
                "service",
                ttl
        );
    }

    private String issue(
            String subject,
            String clientId,
            String tenantId,
            String audience,
            Collection<String> scopes,
            Collection<String> roles,
            String tokenType,
            Duration ttl
    ) {
        validateTtl(ttl);
        Instant now = clock.instant();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(settings.issuer())
                .subject(requireText(subject, "subject"))
                .audience(List.of(requireText(audience, "audience")))
                .issuedAt(now)
                .notBefore(now)
                .expiresAt(now.plus(ttl))
                .id(UUID.randomUUID().toString())
                .claim("tenant_id", requireText(tenantId, "tenantId"))
                .claim("scope", String.join(" ", normalize(scopes)))
                .claim("roles", normalize(roles))
                .claim("token_type", tokenType);
        if (clientId != null) claims.claim("client_id", clientId);
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256)
                        .keyId(settings.keyId())
                        .build(),
                claims.build()
        )).getTokenValue();
    }

    static void validateServiceScopes(Collection<String> scopes) {
        if (scopes == null) return;
        for (String scope : scopes) {
            if (scope == null) continue;
            String normalized = scope.trim();
            if ("*".equals(normalized) || "SCOPE_*".equalsIgnoreCase(normalized))
                throw new IllegalArgumentException("service tokens must not contain wildcard scope");
        }
    }

    private static List<String> normalize(Collection<String> values) {
        if (values == null) return List.of();
        Set<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) normalized.add(value.trim());
        }
        return List.copyOf(normalized);
    }

    private static void validateTtl(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()
                || ttl.compareTo(MAXIMUM_TOKEN_TTL) > 0) {
            throw new IllegalArgumentException(
                    "token ttl must be between 1 second and 15 minutes"
            );
        }
    }

    private static byte[] pem(
            ResourceLoader loader,
            String location,
            String type
    ) throws IOException {
        if (location == null || (!location.startsWith("classpath:")
                && !location.startsWith("file:"))) {
            throw new IllegalArgumentException(
                    "PEM location must start with classpath: or file:"
            );
        }
        Resource resource = loader.getResource(location);
        if (!resource.exists() || !resource.isReadable()) {
            throw new IOException("Cannot read PEM resource: " + location);
        }
        String text;
        try (InputStream input = resource.getInputStream()) {
            text = new String(input.readAllBytes(), StandardCharsets.US_ASCII);
        }
        String begin = "-----BEGIN " + type + "-----";
        String end = "-----END " + type + "-----";
        int start = text.indexOf(begin);
        int finish = text.indexOf(end);
        if (start < 0 || finish < start) {
            throw new IllegalArgumentException("Invalid PEM format: " + location);
        }
        return Base64.getDecoder().decode(
                text.substring(start + begin.length(), finish)
                        .replaceAll("\\s+", "")
        );
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
