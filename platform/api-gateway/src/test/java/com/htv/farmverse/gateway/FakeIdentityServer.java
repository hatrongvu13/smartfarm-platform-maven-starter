package com.htv.farmverse.gateway;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Identity giả cho test luồng đăng nhập của gateway: chỉ hai endpoint back-channel mà gateway gọi,
 * {@code POST /oauth2/token} và {@code GET /oauth2/jwks}. Ký JWT bằng khóa RSA sinh lúc chạy.
 *
 * <p>Trình duyệt không bao giờ tới đây trong test: test tự đọc {@code state}/{@code nonce} từ redirect
 * {@code /oauth2/authorize} rồi gọi thẳng callback của gateway, như trình duyệt sẽ làm sau khi đăng nhập.
 */
final class FakeIdentityServer implements AutoCloseable {

    static final String CLIENT_ID = "farmverse-gateway";
    static final String USER_ID = "user-123";
    static final String TENANT_ID = "tenant-1";

    private final HttpServer server;
    private final RSAKey key;

    /** {@code nonce} phải có trong id_token; test đặt giá trị lấy từ redirect authorize. */
    volatile String nonce;
    /** Body form của lần đổi code gần nhất (để kiểm PKCE {@code code_verifier}). Không chứa secret: client dùng Basic. */
    volatile Map<String, String> lastTokenRequest = Map.of();

    FakeIdentityServer() throws IOException, JOSEException {
        key = new RSAKeyGenerator(2048).keyID("test-key").generate();
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/oauth2/jwks", ex -> json(ex, new JWKSet(key.toPublicJWK()).toString()));
        server.createContext("/oauth2/token", this::token);
        server.start();
    }

    String issuer() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private void token(HttpExchange ex) throws IOException {
        lastTokenRequest = form(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        try {
            Instant now = Instant.now();
            String idToken = sign(new JWTClaimsSet.Builder()
                    .issuer(issuer()).subject("membership-1").audience(CLIENT_ID)
                    .issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(300)))
                    .claim("nonce", nonce).claim("user_id", USER_ID).claim("tenant_id", TENANT_ID)
                    .claim("email", "user@example.test").build());
            String accessToken = sign(new JWTClaimsSet.Builder()
                    .issuer(issuer()).subject(USER_ID).audience(List.of("farm-service", "identity-service"))
                    .issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(300)))
                    .claim("tenant_id", TENANT_ID).claim("perms", List.of("farm:farm:read"))
                    .claim("scope", "openid profile email farmverse.api").build());
            json(ex, "{\"access_token\":\"" + accessToken + "\",\"token_type\":\"Bearer\",\"expires_in\":300,"
                    + "\"refresh_token\":\"" + UUID.randomUUID() + "\",\"scope\":\"openid profile email farmverse.api\","
                    + "\"id_token\":\"" + idToken + "\"}");
        } catch (JOSEException e) {
            ex.sendResponseHeaders(500, -1);
        }
    }

    private String sign(JWTClaimsSet claims) throws JOSEException {
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }

    private static Map<String, String> form(String body) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String pair : body.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                values.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                        URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
            }
        }
        return values;
    }

    private static void json(HttpExchange ex, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
