package com.htv.farmverse.gateway.config;

import java.security.Principal;

import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.oidc.web.server.logout.OidcClientInitiatedServerLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.server.DefaultServerOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.server.WebSessionServerOAuth2AuthorizedClientRepository;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.HttpStatusServerEntryPoint;
import org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository;
import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.security.web.server.csrf.ServerCsrfTokenRequestAttributeHandler;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.session.CookieWebSessionIdResolver;
import org.springframework.web.server.session.WebSessionIdResolver;

/**
 * Gateway làm BFF (Backend for Frontend):
 * <ul>
 *   <li>Đăng nhập OIDC Authorization Code + PKCE với identity-service thay cho trình duyệt.</li>
 *   <li>Token chỉ nằm trong session (Redis); trình duyệt chỉ có cookie {@code FV_SESSION}.</li>
 *   <li>Route {@code /api/**} gắn Bearer xuống service bằng filter {@code TokenRelay} (tự refresh khi hết hạn).</li>
 *   <li>CSRF bằng cookie {@code XSRF-TOKEN} + header {@code X-XSRF-TOKEN} cho mọi request ghi.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@EnableWebFluxSecurity
public class SecurityConfiguration {

    public static final String REGISTRATION_ID = "farmverse";

    @Bean
    SecurityWebFilterChain security(ServerHttpSecurity http, ReactiveClientRegistrationRepository clients) {
        DefaultServerOAuth2AuthorizationRequestResolver pkce = new DefaultServerOAuth2AuthorizationRequestResolver(clients);
        pkce.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());

        OidcClientInitiatedServerLogoutSuccessHandler logout = new OidcClientInitiatedServerLogoutSuccessHandler(clients);
        logout.setPostLogoutRedirectUri("{baseUrl}/");

        http.authorizeExchange(exchange -> exchange
                        .pathMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .pathMatchers("/api/**", "/bff/**").authenticated()
                        // Trang web (SPA) công khai; SPA gọi /bff/me, nhận 401 thì chuyển sang đăng nhập.
                        .anyExchange().permitAll())
                .oauth2Login(login -> login.authorizationRequestResolver(pkce))
                .oauth2Client(Customizer.withDefaults())
                .logout(out -> out.logoutSuccessHandler(logout))
                .exceptionHandling(errors -> errors.authenticationEntryPoint(new HttpStatusServerEntryPoint(HttpStatus.UNAUTHORIZED)))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieServerCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new ServerCsrfTokenRequestAttributeHandler()));
        return http.build();
    }

    /** Token CSRF của WebFlux được tạo lười; filter này buộc tạo để cookie XSRF-TOKEN luôn có cho SPA. */
    @Bean
    WebFilter csrfCookieWebFilter() {
        return (exchange, chain) -> {
            Mono<CsrfToken> token = exchange.getAttributeOrDefault(CsrfToken.class.getName(), Mono.empty());
            return token.then(chain.filter(exchange));
        };
    }

    /** Lưu token đã cấp trong WebSession (Redis), không phải trong bộ nhớ một instance. */
    @Bean
    ServerOAuth2AuthorizedClientRepository authorizedClientRepository() {
        return new WebSessionServerOAuth2AuthorizedClientRepository();
    }

    /** Dùng bởi TokenRelay: lấy token trong session, hết hạn thì refresh với identity. */
    @Bean
    ReactiveOAuth2AuthorizedClientManager authorizedClientManager(ReactiveClientRegistrationRepository clients,
                                                                  ServerOAuth2AuthorizedClientRepository repository) {
        DefaultReactiveOAuth2AuthorizedClientManager manager = new DefaultReactiveOAuth2AuthorizedClientManager(clients, repository);
        manager.setAuthorizedClientProvider(ReactiveOAuth2AuthorizedClientProviderBuilder.builder()
                .authorizationCode()
                .refreshToken()
                .build());
        return manager;
    }

    /** Cookie session riêng tên {@code FV_SESSION}, HttpOnly, SameSite=Lax. */
    @Bean
    WebSessionIdResolver webSessionIdResolver(
            @org.springframework.beans.factory.annotation.Value("${farmverse.gateway.secure-cookie:false}") boolean secure) {
        CookieWebSessionIdResolver resolver = new CookieWebSessionIdResolver();
        resolver.setCookieName("FV_SESSION");
        resolver.addCookieInitializer(cookie -> cookie.httpOnly(true).secure(secure).sameSite("Lax").path("/"));
        return resolver;
    }

    /** Giới hạn tốc độ theo người dùng đã đăng nhập (filter RequestRateLimiter). */
    @Bean
    KeyResolver principalKeyResolver() {
        return exchange -> exchange.getPrincipal().map(Principal::getName).defaultIfEmpty("anonymous");
    }
}
