package com.htv.farmverse.service.mtls;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.boot.ssl.SslBundle;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.client.RestTemplate;

/**
 * Client HTTP gắn chứng chỉ của service (SSL bundle). Dùng cho các cuộc gọi máy-gọi-máy blocking,
 * ví dụ resource server tải JWKS từ identity.
 */
public final class MtlsClients {

    private MtlsClients() {
    }

    public static RestTemplate restTemplate(SslBundle bundle) {
        HttpClient client = HttpClient.newBuilder()
                .sslContext(bundle.createSslContext())
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(Duration.ofSeconds(5));
        return new RestTemplate(factory);
    }

    /** JwtDecoder tải JWKS qua mTLS; validator thường là issuer + audience. */
    public static JwtDecoder jwtDecoder(String jwkSetUri, SslBundle bundle, OAuth2TokenValidator<Jwt> validator) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).restOperations(restTemplate(bundle)).build();
        decoder.setJwtValidator(validator);
        return decoder;
    }
}
