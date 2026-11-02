package com.htv.farmverse.identity.oauth2;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import com.htv.farmverse.identity.config.IdentityProperties.RetiredKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.junit.jupiter.api.Test;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Xoay khóa: token cũ còn hạn vẫn kiểm được, token từ khóa lạ bị từ chối, JWKS không lộ khóa private. */
class SigningKeyRotationTest {

    private final RSAKey oldKey = newKey("key-old");
    private final RSAKey newKey = newKey("key-new");
    private final RetiredKey retiredOld = new RetiredKey("key-old", pem(oldKey));

    @Test
    void publishesActiveAndRetiredKeysWithoutPrivateParts() {
        JWKSet published = SigningKeyConfiguration.publish(newKey, List.of(retiredOld));

        assertThat(published.getKeys()).extracting(k -> k.getKeyID()).containsExactly("key-new", "key-old");
        // JWKS công khai: toJSONObject() mặc định bỏ phần private.
        assertThat(published.toJSONObject().toString()).doesNotContain("\"d\"");
        assertThat(published.getKeyByKeyId("key-old").isPrivate()).isFalse();
    }

    @Test
    void acceptsTokensFromActiveAndRetiredKeys() {
        JwtDecoder decoder = SigningKeyConfiguration.decoder(
                new ImmutableJWKSet<>(SigningKeyConfiguration.publish(newKey, List.of(retiredOld))));

        assertThat(decoder.decode(sign(oldKey)).getSubject()).isEqualTo("u1");
        assertThat(decoder.decode(sign(newKey)).getSubject()).isEqualTo("u1");
    }

    @Test
    void rejectsRetiredTokensOnceKeyIsRemoved() {
        JwtDecoder decoder = SigningKeyConfiguration.decoder(
                new ImmutableJWKSet<>(SigningKeyConfiguration.publish(newKey, List.of())));

        assertThatThrownBy(() -> decoder.decode(sign(oldKey))).isInstanceOf(BadJwtException.class);
        assertThat(decoder.decode(sign(newKey)).getSubject()).isEqualTo("u1");
    }

    @Test
    void rejectsTokensSignedByUnknownKey() {
        JwtDecoder decoder = SigningKeyConfiguration.decoder(
                new ImmutableJWKSet<>(SigningKeyConfiguration.publish(newKey, List.of(retiredOld))));

        assertThatThrownBy(() -> decoder.decode(sign(newKey("key-evil")))).isInstanceOf(BadJwtException.class);
    }

    @Test
    void rejectsDuplicateKeyId() {
        RetiredKey clash = new RetiredKey("key-new", pem(oldKey));

        assertThatThrownBy(() -> SigningKeyConfiguration.publish(newKey, List.of(clash)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("key-new");
    }

    private static String sign(RSAKey key) {
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
        JwtClaimsSet claims = JwtClaimsSet.builder().subject("u1")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).build(), claims)).getTokenValue();
    }

    private static RSAKey newKey(String keyId) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            return new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                    .privateKey((RSAPrivateKey) pair.getPrivate()).keyID(keyId).build();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Khóa công khai dạng PEM (X.509), giống file {@code *-public.pem} do script sinh. */
    private static ByteArrayResource pem(RSAKey key) {
        try {
            String body = Base64.getMimeEncoder(64, "\n".getBytes())
                    .encodeToString(key.toRSAPublicKey().getEncoded());
            return new ByteArrayResource(("-----BEGIN PUBLIC KEY-----\n" + body + "\n-----END PUBLIC KEY-----\n").getBytes());
        } catch (com.nimbusds.jose.JOSEException e) {
            throw new IllegalStateException(e);
        }
    }
}
