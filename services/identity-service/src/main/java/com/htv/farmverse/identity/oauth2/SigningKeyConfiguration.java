package com.htv.farmverse.identity.oauth2;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import com.htv.farmverse.identity.config.IdentityProperties;
import com.htv.farmverse.service.security.FarmVerseClaims;
import com.htv.farmverse.service.security.FarmVerseJwtValidators;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Khóa ký token: một cặp RSA đọc từ PEM ({@code scripts/generate-dev-keys.sh} tạo bản dev).
 * Khóa công khai được công bố ở {@code /oauth2/jwks} để resource server tự kiểm chữ ký.
 */
@Configuration(proxyBeanMethods = false)
public class SigningKeyConfiguration {

    @Bean
    RSAKey signingKey(IdentityProperties properties) {
        IdentityProperties.SigningKey key = properties.signingKey();
        if (key.ephemeral()) {
            return generate(key.keyId());
        }
        RSAPrivateKey privateKey = read(key.privateKey(), true);
        RSAPublicKey publicKey = read(key.publicKey(), false);
        return new RSAKey.Builder(publicKey).privateKey(privateKey).keyID(key.keyId()).build();
    }

    @Bean
    JWKSource<SecurityContext> jwkSource(RSAKey signingKey) {
        return new ImmutableJWKSet<>(new JWKSet(signingKey));
    }

    /**
     * Dùng cho endpoint /userinfo của SAS và cho REST quản trị của chính identity:
     * kiểm chữ ký bằng khóa công khai, issuer và audience {@value FarmVerseClaims#API_AUDIENCE}.
     */
    @Bean
    JwtDecoder jwtDecoder(RSAKey signingKey, IdentityProperties properties) throws Exception {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(signingKey.toRSAPublicKey()).build();
        decoder.setJwtValidator(FarmVerseJwtValidators.issuerAndAudience(properties.issuer(), FarmVerseClaims.API_AUDIENCE));
        return decoder;
    }

    private static RSAKey generate(String keyId) {
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

    @SuppressWarnings("unchecked")
    private static <T> T read(Resource resource, boolean privateKey) {
        try (InputStream in = resource.getInputStream()) {
            return (T) (privateKey ? RsaKeyConverters.pkcs8().convert(in) : RsaKeyConverters.x509().convert(in));
        } catch (IOException e) {
            throw new IllegalStateException("Không đọc được khóa " + resource
                    + " — chạy scripts/generate-dev-keys.sh để tạo khóa dev", e);
        }
    }
}
