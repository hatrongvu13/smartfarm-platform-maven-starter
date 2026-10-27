package com.htv.farmverse.gateway.config;

import java.time.Duration;
import java.util.Map;

import javax.net.ssl.SSLException;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import reactor.netty.http.client.HttpClient;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.security.oauth2.client.endpoint.ReactiveOAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.endpoint.OAuth2RefreshTokenGrantRequest;
import org.springframework.security.oauth2.client.endpoint.WebClientReactiveAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.WebClientReactiveRefreshTokenTokenResponseClient;
import org.springframework.security.oauth2.client.oidc.authentication.OidcAuthorizationCodeReactiveAuthenticationManager;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenValidator;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcReactiveOAuth2UserService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoderFactory;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Mọi cuộc gọi gateway → identity (đổi code, refresh, JWKS để kiểm id_token) đi qua một {@link WebClient}
 * gắn chứng chỉ client của gateway (mTLS). Trình duyệt chỉ đi tới {@code /oauth2/authorize} và {@code /connect/logout}.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GatewayOidcProperties.class)
public class OidcClientConfiguration {

    @Bean
    WebClient identityWebClient(GatewayOidcProperties properties, SslBundles bundles) throws SSLException {
        HttpClient http = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(5))
                .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000);
        if (properties.mtls()) {
            SslContext ssl = nettyClientContext(bundles.getBundle(properties.sslBundle()));
            http = http.secure(spec -> spec.sslContext(ssl));
        }
        return WebClient.builder().clientConnector(new ReactorClientHttpConnector(http)).build();
    }

    static SslContext nettyClientContext(SslBundle bundle) throws SSLException {
        return SslContextBuilder.forClient()
                .keyManager(bundle.getManagers().getKeyManagerFactory())
                .trustManager(bundle.getManagers().getTrustManagerFactory())
                .build();
    }

    /** Đăng ký client tĩnh (không discovery). {@code end_session_endpoint} để đăng xuất cả phía identity. */
    @Bean
    ReactiveClientRegistrationRepository clientRegistrations(GatewayOidcProperties p) {
        ClientRegistration registration = ClientRegistration.withRegistrationId(SecurityConfiguration.REGISTRATION_ID)
                .clientId(p.clientId())
                .clientSecret(p.clientSecret())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope(p.scopes())
                .issuerUri(p.issuer())
                .authorizationUri(p.authorizationUri())
                .tokenUri(p.tokenUri())
                .jwkSetUri(p.jwkSetUri())
                .userNameAttributeName(IdTokenClaimNames.SUB)
                .providerConfigurationMetadata(Map.of("end_session_endpoint", p.endSessionUri()))
                .build();
        return new InMemoryReactiveClientRegistrationRepository(registration);
    }

    @Bean
    ReactiveOAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> authorizationCodeTokenClient(WebClient identityWebClient) {
        WebClientReactiveAuthorizationCodeTokenResponseClient client = new WebClientReactiveAuthorizationCodeTokenResponseClient();
        client.setWebClient(identityWebClient);
        return client;
    }

    @Bean
    ReactiveOAuth2AccessTokenResponseClient<OAuth2RefreshTokenGrantRequest> refreshTokenClient(WebClient identityWebClient) {
        WebClientReactiveRefreshTokenTokenResponseClient client = new WebClientReactiveRefreshTokenTokenResponseClient();
        client.setWebClient(identityWebClient);
        return client;
    }

    /** Kiểm id_token: chữ ký (JWKS qua mTLS), iss, aud = client id, hạn dùng. */
    @Bean
    ReactiveJwtDecoderFactory<ClientRegistration> idTokenDecoderFactory(WebClient identityWebClient) {
        return registration -> {
            NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder
                    .withJwkSetUri(registration.getProviderDetails().getJwkSetUri())
                    .webClient(identityWebClient)
                    .build();
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                    new JwtTimestampValidator(), new OidcIdTokenValidator(registration)));
            return decoder;
        };
    }

    @Bean
    OidcAuthorizationCodeReactiveAuthenticationManager oidcLoginManager(
            ReactiveOAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> tokenClient,
            ReactiveJwtDecoderFactory<ClientRegistration> idTokenDecoderFactory) {
        OidcAuthorizationCodeReactiveAuthenticationManager manager =
                new OidcAuthorizationCodeReactiveAuthenticationManager(tokenClient, new OidcReactiveOAuth2UserService());
        manager.setJwtDecoderFactory(idTokenDecoderFactory);
        return manager;
    }
}
