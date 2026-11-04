package com.htv.farmverse.gateway.config;

import java.net.URI;
import java.security.Principal;

import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.security.oauth2.client.endpoint.OAuth2RefreshTokenGrantRequest;
import org.springframework.security.oauth2.client.endpoint.ReactiveOAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.oidc.authentication.OidcAuthorizationCodeReactiveAuthenticationManager;
import org.springframework.security.oauth2.client.oidc.web.server.logout.OidcClientInitiatedServerLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.server.DefaultServerOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.server.WebSessionServerOAuth2AuthorizedClientRepository;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository;
import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.security.web.server.csrf.ServerCsrfTokenRequestAttributeHandler;
import org.springframework.security.web.server.header.ReferrerPolicyServerHttpHeadersWriter.ReferrerPolicy;
import org.springframework.security.web.server.header.StaticServerHttpHeadersWriter;
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

    /** Chỗ giữ trong CSP cho origin của identity (đích redirect của form đăng xuất). */
    static final String IDENTITY_ORIGIN = "{identity-origin}";
    static final String HSTS = "max-age=31536000";

    @Bean
    SecurityWebFilterChain security(ServerHttpSecurity http, ReactiveClientRegistrationRepository clients,
                                    OidcAuthorizationCodeReactiveAuthenticationManager oidcLogin,
                                    GatewayOidcProperties oidc,
                                    @Value("${farmverse.gateway.headers.content-security-policy}") String csp,
                                    @Value("${farmverse.gateway.headers.hsts:false}") boolean hsts) {
        DefaultServerOAuth2AuthorizationRequestResolver pkce = new DefaultServerOAuth2AuthorizationRequestResolver(clients);
        pkce.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());

        OidcClientInitiatedServerLogoutSuccessHandler logout = new OidcClientInitiatedServerLogoutSuccessHandler(clients);
        logout.setPostLogoutRedirectUri("{baseUrl}/");

        http.authorizeExchange(exchange -> exchange
                        .pathMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .pathMatchers("/api/**", "/bff/**").authenticated()
                        // Trang web (SPA) công khai; SPA gọi /bff/me, nhận 401 thì chuyển sang đăng nhập.
                        .anyExchange().permitAll())
                .oauth2Login(login -> login
                        .authorizationRequestResolver(pkce)
                        .authenticationManager(oidcLogin))   // đổi code + kiểm id_token qua mTLS
                .oauth2Client(Customizer.withDefaults())
                .logout(out -> out.logoutSuccessHandler(logout))
                // 401/403 cùng dạng ProblemDetail với service phía sau (SF-AUTH-001/002). CSRF có handler riêng,
                // nên phải gắn lại ở csrf(...), nếu không lỗi thiếu token CSRF vẫn là text/plain "Access Denied".
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(SecurityConfiguration::unauthorized)
                        .accessDeniedHandler(SecurityConfiguration::forbidden))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieServerCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new ServerCsrfTokenRequestAttributeHandler())
                        .accessDeniedHandler(SecurityConfiguration::forbidden))
                .headers(headers -> securityHeaders(headers, csp.replace(IDENTITY_ORIGIN, origin(oidc.issuer())), hsts));
        return http.build();
    }

    /**
     * Header bảo mật cho mọi phản hồi, kể cả trang SPA mà gateway proxy (route {@code web}) và lỗi 401/403.
     * Giữ mặc định của Spring (nosniff, X-Frame-Options DENY, Cache-Control cho phản hồi động) và thêm:
     * <ul>
     *   <li>CSP lấy từ {@code farmverse.gateway.headers.content-security-policy}: prod chỉ {@code 'self'};
     *       dev nới cho Vite (script/style nhúng, websocket HMR). {@code form-action} gồm origin identity vì form
     *       đăng xuất redirect sang {@code /connect/logout} của identity, và trình duyệt áp CSP cả cho redirect đó.</li>
     *   <li>HSTS: bộ ghi của Spring chỉ ghi khi request là HTTPS. Ở prod TLS kết thúc ở edge nên gateway thấy HTTP,
     *       vì vậy tắt bộ ghi đó và tự ghi khi bật {@code farmverse.gateway.headers.hsts}. Trình duyệt bỏ qua HSTS
     *       nhận qua HTTP, nên ghi luôn không gây hại. Không {@code includeSubDomains}: ảnh hưởng tên miền khác.</li>
     * </ul>
     */
    private static void securityHeaders(ServerHttpSecurity.HeaderSpec headers, String csp, boolean hsts) {
        headers.contentSecurityPolicy(policy -> policy.policyDirectives(csp))
                .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .permissionsPolicy(permissions -> permissions.policy("camera=(), microphone=(), geolocation=(), payment=()"))
                .hsts(ServerHttpSecurity.HeaderSpec.HstsSpec::disable);
        if (hsts) {
            headers.writer(StaticServerHttpHeadersWriter.builder().header("Strict-Transport-Security", HSTS).build());
        }
    }

    private static Mono<Void> unauthorized(org.springframework.web.server.ServerWebExchange exchange,
                                           org.springframework.security.core.AuthenticationException error) {
        return GatewayProblems.write(exchange, HttpStatus.UNAUTHORIZED, GatewayProblems.UNAUTHORIZED, "Cần đăng nhập");
    }

    private static Mono<Void> forbidden(org.springframework.web.server.ServerWebExchange exchange,
                                        org.springframework.security.access.AccessDeniedException error) {
        return GatewayProblems.write(exchange, HttpStatus.FORBIDDEN, GatewayProblems.FORBIDDEN,
                "Không được phép thực hiện thao tác này");
    }

    /** {@code https://id.example.com/idp} → {@code https://id.example.com}: CSP so khớp theo origin. */
    static String origin(String url) {
        URI uri = URI.create(url);
        return uri.getScheme() + "://" + uri.getRawAuthority();
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
                                                                  ServerOAuth2AuthorizedClientRepository repository,
                                                                  ReactiveOAuth2AccessTokenResponseClient<OAuth2RefreshTokenGrantRequest> refreshClient) {
        DefaultReactiveOAuth2AuthorizedClientManager manager = new DefaultReactiveOAuth2AuthorizedClientManager(clients, repository);
        manager.setAuthorizedClientProvider(ReactiveOAuth2AuthorizedClientProviderBuilder.builder()
                .authorizationCode()
                .refreshToken(refresh -> refresh.accessTokenResponseClient(refreshClient))
                .build());
        return manager;
    }

    /** Cookie session riêng tên {@code FV_SESSION}, HttpOnly, SameSite=Lax. */
    @Bean
    WebSessionIdResolver webSessionIdResolver(@Value("${farmverse.gateway.secure-cookie:false}") boolean secure) {
        CookieWebSessionIdResolver resolver = new CookieWebSessionIdResolver();
        resolver.setCookieName("FV_SESSION");
        resolver.addCookieInitializer(cookie -> cookie.httpOnly(true).secure(secure).sameSite("Lax").path("/"));
        return resolver;
    }

    /**
     * Tên bean phải đúng {@code forwardedHeaderTransformer}: {@code WebHttpHandlerBuilder} tìm theo tên này.
     * Danh sách rỗng (mặc định, dev) = không tin header forwarded của ai.
     */
    @Bean(name = "forwardedHeaderTransformer")
    TrustedProxyForwardedHeaderTransformer forwardedHeaderTransformer(
            @Value("${farmverse.gateway.trusted-proxies:}") java.util.List<String> trustedProxies,
            org.springframework.core.env.Environment environment) {
        // Gateway không tự kết thúc TLS: ở prod luôn có edge phía trước. Thiếu danh sách thì redirect_uri thành
        // http://<máy nội bộ> và identity từ chối đăng nhập với lỗi khó lần ra; dừng ngay khi khởi động thì rõ hơn.
        if (trustedProxies.stream().allMatch(String::isBlank)
                && environment.acceptsProfiles(org.springframework.core.env.Profiles.of("prod"))) {
            throw new IllegalStateException("Profile prod cần GATEWAY_TRUSTED_PROXIES (IP/CIDR của edge TLS)");
        }
        return new TrustedProxyForwardedHeaderTransformer(trustedProxies);
    }

    /** Giới hạn tốc độ theo người dùng đã đăng nhập (filter RequestRateLimiter). */
    @Bean
    KeyResolver principalKeyResolver() {
        return exchange -> exchange.getPrincipal().map(Principal::getName).defaultIfEmpty("anonymous");
    }
}
