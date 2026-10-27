package com.htv.farmverse.identity.oauth2;

import java.util.UUID;

import com.htv.farmverse.identity.config.IdentityProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Component;

/**
 * Nạp client OAuth2 khai trong {@code farmverse.identity.clients} vào bảng của SAS lúc khởi động.
 * Client đã có thì ghi đè (giữ id) để đổi cấu hình chỉ cần sửa YAML và khởi động lại.
 */
@Slf4j
@Order(1)
@Component
@RequiredArgsConstructor
public class OAuthClientProvisioner implements ApplicationRunner {

    private final RegisteredClientRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final IdentityProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        for (IdentityProperties.Client client : properties.clients()) {
            RegisteredClient existing = repository.findByClientId(client.clientId());
            String id = existing == null ? UUID.randomUUID().toString() : existing.getId();
            repository.save(toRegisteredClient(id, client));
            log.info("OAuth client ready: {}", client);
        }
    }

    private RegisteredClient toRegisteredClient(String id, IdentityProperties.Client client) {
        boolean authorizationCode = client.grantTypes().contains(AuthorizationGrantType.AUTHORIZATION_CODE.getValue());
        RegisteredClient.Builder builder = RegisteredClient.withId(id)
                .clientId(client.clientId())
                .clientSecret(passwordEncoder.encode(client.clientSecret()))
                .clientName(client.name())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .clientSettings(ClientSettings.builder()
                        .requireProofKey(authorizationCode)      // PKCE bắt buộc cho authorization_code
                        .requireAuthorizationConsent(false)      // client nội bộ, không hỏi đồng ý
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(client.accessTokenTtl())
                        .refreshTokenTimeToLive(client.refreshTokenTtl())
                        .reuseRefreshTokens(false)               // xoay vòng refresh token mỗi lần dùng
                        .idTokenSignatureAlgorithm(SignatureAlgorithm.RS256)
                        .build());
        client.grantTypes().forEach(grant -> builder.authorizationGrantType(new AuthorizationGrantType(grant)));
        client.redirectUris().forEach(builder::redirectUri);
        client.postLogoutRedirectUris().forEach(builder::postLogoutRedirectUri);
        client.scopes().forEach(builder::scope);
        return builder.build();
    }
}
