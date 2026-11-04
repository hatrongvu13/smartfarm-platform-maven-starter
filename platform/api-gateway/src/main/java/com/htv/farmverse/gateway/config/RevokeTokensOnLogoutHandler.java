package com.htv.farmverse.gateway.config;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizedClientRepository;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.logout.ServerLogoutHandler;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

/**
 * Đăng xuất thu hồi refresh token ở identity (RFC 7009, {@code POST /oauth2/revoke}), rồi xóa token khỏi phiên.
 *
 * <p>Trước đây đăng xuất chỉ xóa phiên của gateway và phiên SSO của identity. Refresh token (14 ngày) vẫn dùng được
 * nếu đã bị lộ (bản sao Redis, log). Identity thu hồi refresh token thì cũng vô hiệu access token của cùng lần cấp
 * ở phía identity; resource server vẫn nhận access token cũ cho tới khi hết hạn (tối đa 5 phút), vì chúng kiểm JWT
 * tại chỗ.
 *
 * <p>Chạy TRƯỚC khi xóa security context và phiên, vì cần token trong phiên. Identity lỗi hay chậm thì vẫn đăng xuất:
 * ghi cảnh báo và đi tiếp, không giữ người dùng ở trạng thái đăng nhập. Không ghi giá trị token vào log.
 */
public final class RevokeTokensOnLogoutHandler implements ServerLogoutHandler {

    private static final Logger log = LoggerFactory.getLogger(RevokeTokensOnLogoutHandler.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final ServerOAuth2AuthorizedClientRepository authorizedClients;
    private final WebClient identity;
    private final String revocationUri;

    public RevokeTokensOnLogoutHandler(ServerOAuth2AuthorizedClientRepository authorizedClients, WebClient identity,
                                       String revocationUri) {
        this.authorizedClients = authorizedClients;
        this.identity = identity;
        this.revocationUri = revocationUri;
    }

    @Override
    public Mono<Void> logout(WebFilterExchange exchange, Authentication authentication) {
        if (!(authentication instanceof OAuth2AuthenticationToken token)) {
            return Mono.empty();
        }
        String registrationId = token.getAuthorizedClientRegistrationId();
        return authorizedClients.<OAuth2AuthorizedClient>loadAuthorizedClient(registrationId, authentication, exchange.getExchange())
                .flatMap(client -> revoke(client)
                        .then(authorizedClients.removeAuthorizedClient(registrationId, authentication, exchange.getExchange())))
                .onErrorResume(e -> {
                    log.warn("Refresh token revocation failed on logout, continuing logout: {}", e.getClass().getSimpleName());
                    return Mono.empty();
                });
    }

    private Mono<Void> revoke(OAuth2AuthorizedClient client) {
        if (client.getRefreshToken() == null) {
            return Mono.empty();
        }
        ClientRegistration registration = client.getClientRegistration();
        return identity.post().uri(revocationUri)
                .headers(headers -> headers.setBasicAuth(encode(registration.getClientId()), encode(registration.getClientSecret())))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromFormData("token", client.getRefreshToken().getTokenValue())
                        .with("token_type_hint", "refresh_token"))
                .retrieve()
                .toBodilessEntity()
                .timeout(TIMEOUT)
                .then();
    }

    /** RFC 6749 §2.3.1: client id và secret được URL-encode trước khi ghép Basic. */
    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
