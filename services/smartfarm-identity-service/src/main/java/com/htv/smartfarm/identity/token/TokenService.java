package com.htv.smartfarm.identity.token;

import com.htv.smartfarm.identity.config.IdentitySettings;
import com.htv.smartfarm.identity.user.UserRepository;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.*;
import java.time.Instant;
import java.util.*;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.stereotype.Component;

@Component
public class TokenService {
    private final IdentitySettings settings;
    private final RSAKey key;
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;

    public TokenService(
            IdentitySettings settings,
            ResourceLoader resourceLoader
    ) throws Exception {
        this.settings = settings;

        KeyFactory factory = KeyFactory.getInstance("RSA");

        byte[] privateBytes = pem(
                resourceLoader,
                settings.privateKeyPath(),
                "PRIVATE KEY"
        );
        byte[] publicBytes = pem(
                resourceLoader,
                settings.publicKeyPath(),
                "PUBLIC KEY"
        );

        RSAPrivateKey privateKey = (RSAPrivateKey) factory.generatePrivate(
                new PKCS8EncodedKeySpec(privateBytes)
        );
        RSAPublicKey publicKey = (RSAPublicKey) factory.generatePublic(
                new X509EncodedKeySpec(publicBytes)
        );

        if (publicKey.getModulus().bitLength() < 2048 || !privateKey.getModulus().equals(publicKey.getModulus()))
            throw new IllegalArgumentException("RSA key pair mismatch or key smaller than 2048 bits");
        key = new RSAKey.Builder(publicKey).privateKey(privateKey).keyID(settings.keyId()).keyUse(KeyUse.SIGNATURE).algorithm(JWSAlgorithm.RS256).build();
        encoder = new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(key)));
        NimbusJwtDecoder d = NimbusJwtDecoder.withPublicKey(publicKey).signatureAlgorithm(SignatureAlgorithm.RS256).build();
        d.setJwtValidator(jwt -> {
            var base = JwtValidators.createDefaultWithIssuer(settings.issuer()).validate(jwt);
            if (base.hasErrors()) return base;
            if (!jwt.getAudience().contains(settings.gatewayAudience()) || jwt.getSubject() == null || jwt.getSubject().isBlank()
                    || jwt.getExpiresAt() == null || jwt.getClaimAsString("tenant_id") == null)
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "invalid audience or identity", null));
            return OAuth2TokenValidatorResult.success();
        });
        decoder = d;
    }

    private static byte[] pem(
            ResourceLoader resourceLoader,
            String location,
            String type
    ) throws IOException {
        if (location == null || location.isBlank()) {
            throw new IllegalArgumentException("Missing PEM location for " + type);
        }

        // Chỉ chấp nhận classpath hoặc file, không tải khóa từ URL bên ngoài.
        if (!location.startsWith("classpath:")
                && !location.startsWith("file:")) {
            throw new IllegalArgumentException(
                    "PEM location must start with classpath: or file:"
            );
        }

        Resource resource = resourceLoader.getResource(location);

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
            throw new IllegalArgumentException(
                    "Invalid PEM format for " + type + " at " + location
            );
        }

        String base64 = text.substring(start + begin.length(), finish)
                .replaceAll("\\s+", "");

        try {
            return Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "Invalid Base64 PEM content at " + location,
                    ex
            );
        }
    }

    public JwtDecoder decoder() {
        return decoder;
    }

    public String jwks() {
        return new JWKSet(key.toPublicJWK()).toString();
    }

    public String issue(UserRepository.User user, Set<String> scopes, Set<String> roles) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(settings.issuer()).subject(user.id())
                .audience(List.of(settings.gatewayAudience())).issuedAt(now).notBefore(now).expiresAt(now.plus(settings.accessTtl()))
                .id(UUID.randomUUID().toString()).claim("tenant_id", user.tenantId()).claim("scope", String.join(" ", scopes))
                .claim("roles", List.copyOf(roles)).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).keyId(settings.keyId()).build(), claims)).getTokenValue();
    }

    /**
     * Mint a short-lived machine-to-machine (service) token for the client-credentials
     * flow (Hướng A). The subject is {@code svc:<clientId>} so audit logs can distinguish
     * a machine caller from a human user, and the audience is the SPECIFIC downstream
     * service the caller is allowed to reach — so this token cannot be replayed against a
     * different service. The human actor that triggered the call is NOT embedded here; it
     * rides in {@code RequestContext.actor_id}, set by the gateway after verifying the
     * user's JWT, and is logged at each hop for end-to-end traceability.
     *
     * @param clientId caller identity (e.g. {@code gateway})
     * @param audience downstream service audience (e.g. {@code smartfarm-livestock})
     * @param tenantId tenant the call operates within
     * @param scopes   least-privilege scopes granted for this audience
     * @param ttl      token lifetime (capped by config; must not exceed 15 minutes)
     */
    public String issueServiceToken(String clientId, String audience, String tenantId,
                                    java.util.Collection<String> scopes, java.time.Duration ttl) {
        if (clientId == null || clientId.isBlank() || audience == null || audience.isBlank()
                || tenantId == null || tenantId.isBlank())
            throw new IllegalArgumentException("clientId, audience and tenantId required");
        if (ttl == null || ttl.isNegative() || ttl.isZero() || ttl.compareTo(java.time.Duration.ofMinutes(15)) > 0)
            throw new IllegalArgumentException("service token ttl must be between 1 second and 15 minutes");
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(settings.issuer()).subject("svc:" + clientId)
                .audience(List.of(audience)).issuedAt(now).notBefore(now).expiresAt(now.plus(ttl))
                .id(UUID.randomUUID().toString()).claim("tenant_id", tenantId)
                .claim("scope", String.join(" ", scopes == null ? List.of() : scopes))
                .claim("client_id", clientId).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).keyId(settings.keyId()).build(), claims)).getTokenValue();
    }
}
