package com.htv.farmverse.farm;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M6, R8/R9/R13 bằng token KÝ THẬT đi qua bộ giải mã thật của Boot (không dùng {@code jwt()} giả của spring-security-test,
 * vốn bỏ qua mọi bước kiểm token). Identity giả chỉ phục vụ JWKS.
 *
 * <ul>
 *   <li>Chữ ký, {@code iss}, {@code exp}, {@code nbf}, {@code aud} sai → 401.</li>
 *   <li>Token đúng nhưng thiếu scope {@code farmverse.api} → 403.</li>
 *   <li>Farm không gọi identity mỗi request: JWKS tải một lần rồi dùng cache; gặp {@code kid} mới mới tải lại.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TokenValidationTest {

    static final RSAKey KEY_1 = rsa("key-1");
    static final RSAKey KEY_2 = rsa("key-2");          // khóa mới sau khi identity xoay khóa
    static final RSAKey FOREIGN = rsa("key-1");        // cùng kid, khóa của kẻ khác
    static volatile List<RSAKey> published = List.of(KEY_1);
    static final AtomicInteger jwksCalls = new AtomicInteger();
    static final HttpServer JWKS = jwks();

    static String issuer() {
        return "http://127.0.0.1:" + JWKS.getAddress().getPort();
    }

    @DynamicPropertySource
    static void identity(DynamicPropertyRegistry registry) {
        registry.add("IDENTITY_ISSUER", TokenValidationTest::issuer);
        registry.add("IDENTITY_JWKS_URI", () -> issuer() + "/oauth2/jwks");
    }

    @AfterAll
    static void stop() {
        JWKS.stop(0);
    }

    @Autowired MockMvc mvc;

    record Claims(String issuer, List<String> aud, String scope, Instant exp, Instant nbf) {
        static Claims good() {
            return new Claims(TokenValidationTest.issuer(), List.of("farm-service", "identity-service"), "openid farmverse.api",
                    Instant.now().plusSeconds(300), null);
        }
    }

    private static String sign(RSAKey key, Claims c) throws Exception {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .issuer(c.issuer()).subject("user-1").audience(c.aud())
                .issueTime(new Date()).expirationTime(Date.from(c.exp()))
                .claim("scope", c.scope()).claim("tenant_id", "tenant-1")
                .claim("perms", List.of("farm:farm:read"));
        if (c.nbf() != null) {
            claims.notBeforeTime(Date.from(c.nbf()));
        }
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims.build());
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }

    private org.springframework.test.web.servlet.ResultActions call(String token) throws Exception {
        return mvc.perform(get("/farms").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    @Test
    void validTokenForFarmIsAccepted() throws Exception {
        call(sign(KEY_1, Claims.good())).andExpect(status().isOk());
    }

    @Test
    void wrongAudienceIsRejected() throws Exception {
        Claims c = Claims.good();
        call(sign(KEY_1, new Claims(c.issuer(), List.of("identity-service"), c.scope(), c.exp(), null)))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("SF-AUTH-001"));
        // audience chung cũ không còn được nhận
        call(sign(KEY_1, new Claims(c.issuer(), List.of("farmverse-api"), c.scope(), c.exp(), null)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongIssuerExpiredOrNotYetValidIsRejected() throws Exception {
        Claims c = Claims.good();
        call(sign(KEY_1, new Claims("https://evil.test", c.aud(), c.scope(), c.exp(), null)))
                .andExpect(status().isUnauthorized());
        call(sign(KEY_1, new Claims(c.issuer(), c.aud(), c.scope(), Instant.now().minusSeconds(600), null)))
                .andExpect(status().isUnauthorized());
        call(sign(KEY_1, new Claims(c.issuer(), c.aud(), c.scope(), c.exp(), Instant.now().plusSeconds(600))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void forgedSignatureIsRejected() throws Exception {
        call(sign(FOREIGN, Claims.good())).andExpect(status().isUnauthorized());
    }

    @Test
    void missingApiScopeIs403EvenWithPermissions() throws Exception {
        Claims c = Claims.good();
        call(sign(KEY_1, new Claims(c.issuer(), c.aud(), "openid profile", c.exp(), null)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("SF-AUTH-002"));
    }

    /** R13: không gọi identity mỗi request; xoay khóa (kid mới) thì tải lại JWKS một lần và chạy tiếp. */
    @Test
    void jwksIsCachedAndRefetchedOnlyForANewKey() throws Exception {
        call(sign(KEY_1, Claims.good())).andExpect(status().isOk());
        int afterWarmUp = jwksCalls.get();
        for (int i = 0; i < 5; i++) {
            call(sign(KEY_1, Claims.good())).andExpect(status().isOk());
        }
        assertThat(jwksCalls.get()).as("5 request với khóa đã biết không gọi identity").isEqualTo(afterWarmUp);

        published = List.of(KEY_2, KEY_1);   // identity công bố khóa mới, giữ khóa cũ
        call(sign(KEY_2, Claims.good())).andExpect(status().isOk());
        assertThat(jwksCalls.get()).as("kid mới: tải lại JWKS").isGreaterThan(afterWarmUp);
        call(sign(KEY_1, Claims.good())).andExpect(status().isOk());   // token ký bằng khóa cũ, còn hạn: vẫn dùng được
    }

    // ---- identity giả: chỉ JWKS ----

    static RSAKey rsa(String kid) {
        try {
            return new RSAKeyGenerator(2048).keyID(kid).generate();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static HttpServer jwks() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/oauth2/jwks", ex -> {
                jwksCalls.incrementAndGet();
                byte[] body = new JWKSet(published.stream().map(k -> (com.nimbusds.jose.jwk.JWK) k.toPublicJWK()).toList())
                        .toString().getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().set("Content-Type", "application/json");
                ex.sendResponseHeaders(200, body.length);
                try (OutputStream out = ex.getResponseBody()) {
                    out.write(body);
                }
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
