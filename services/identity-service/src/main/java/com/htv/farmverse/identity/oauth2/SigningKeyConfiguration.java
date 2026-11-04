package com.htv.farmverse.identity.oauth2;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.htv.farmverse.identity.config.IdentityProperties;
import com.htv.farmverse.service.security.FarmVerseClaims;
import com.htv.farmverse.service.security.FarmVerseJwtValidators;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Khóa ký token và xoay khóa.
 *
 * <ul>
 *   <li>Chỉ <b>một</b> khóa đang ký ({@code signing-key}), đọc từ PEM
 *       ({@code scripts/generate-dev-keys.sh} tạo bản dev).</li>
 *   <li>Các khóa đã nghỉ ({@code signing-key.retired}) chỉ còn khóa công khai. JWKS ở
 *       {@code /oauth2/jwks} công bố cả hai loại, nên resource server vẫn kiểm được token cũ còn hạn.</li>
 * </ul>
 *
 * Quy trình xoay khóa: {@code scripts/rotate-signing-key.sh}, xem {@code docs/operations/key-rotation.md}.
 */
@Configuration(proxyBeanMethods = false)
public class SigningKeyConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SigningKeyConfiguration.class);

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

    /** Nguồn khóa để công bố JWKS và để kiểm chữ ký: khóa đang ký + khóa đã nghỉ. */
    @Bean
    JWKSource<SecurityContext> jwkSource(RSAKey signingKey, IdentityProperties properties) {
        JWKSet published = publish(signingKey, properties.signingKey().retired());
        log.info("JWKS công bố {} khóa: đang ký={}, đã nghỉ={}", published.getKeys().size(),
                signingKey.getKeyID(), properties.signingKey().retired().stream()
                        .map(IdentityProperties.RetiredKey::keyId).toList());
        return new ImmutableJWKSet<>(published);
    }

    /**
     * Bộ ký của SAS: chỉ thấy khóa đang ký. Nếu để SAS tự dựng từ {@link JWKSource} có nhiều khóa,
     * nó có thể không chọn được khóa ký.
     */
    @Bean
    JwtEncoder jwtEncoder(RSAKey signingKey) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(signingKey)));
    }

    /**
     * Dùng cho endpoint /userinfo của SAS và cho REST quản trị của chính identity:
     * kiểm chữ ký theo {@code kid} (khóa đang ký hoặc đã nghỉ), issuer và audience
     * {@value FarmVerseClaims#IDENTITY_AUDIENCE} ({@code farmverse.identity.accepted-audiences}; lúc chuyển đổi có thể
     * tạm thêm {@value FarmVerseClaims#LEGACY_API_AUDIENCE}).
     */
    @Bean
    JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource, IdentityProperties properties,
                          @org.springframework.beans.factory.annotation.Value(
                                  "${farmverse.identity.accepted-audiences:" + FarmVerseClaims.IDENTITY_AUDIENCE + "}")
                          java.util.List<String> acceptedAudiences) {
        NimbusJwtDecoder decoder = decoder(jwkSource);
        decoder.setJwtValidator(FarmVerseJwtValidators.issuerAndAudience(properties.issuer(), acceptedAudiences));
        return decoder;
    }

    static NimbusJwtDecoder decoder(JWKSource<SecurityContext> jwkSource) {
        DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, jwkSource));
        // Hạn dùng, issuer, audience do validator của Spring kiểm.
        processor.setJWTClaimsSetVerifier((claims, context) -> { });
        return new NimbusJwtDecoder(processor);
    }

    /** Gộp khóa đang ký (đủ cặp) với các khóa đã nghỉ (chỉ khóa công khai). Từ chối {@code kid} trùng. */
    static JWKSet publish(RSAKey active, List<IdentityProperties.RetiredKey> retired) {
        List<JWK> keys = new ArrayList<>();
        keys.add(active);
        Set<String> ids = new HashSet<>();
        ids.add(active.getKeyID());
        for (IdentityProperties.RetiredKey old : retired) {
            if (!ids.add(old.keyId())) {
                throw new IllegalStateException("Khóa đã nghỉ trùng key-id '" + old.keyId()
                        + "' với khóa đang ký hoặc một khóa nghỉ khác");
            }
            RSAPublicKey publicKey = read(old.publicKey(), false);
            keys.add(new RSAKey.Builder(publicKey).keyID(old.keyId()).build());
        }
        return new JWKSet(keys);
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
