package com.htv.farmverse.identity.config;

import java.time.Clock;

import com.htv.farmverse.identity.account.AccountService;
import com.htv.farmverse.identity.login.AccountUserDetailsService;
import com.htv.farmverse.identity.login.LoginRateLimitFilter;
import com.htv.farmverse.identity.login.TenantAuthenticationProvider;
import com.htv.farmverse.identity.login.TenantLoginDetails;
import com.htv.farmverse.identity.login.TotpEnforcementFilter;
import com.htv.farmverse.identity.login.TotpLogin;
import com.htv.farmverse.identity.mfa.TotpService;
import com.htv.farmverse.identity.tenant.MembershipRepository;
import com.htv.farmverse.service.security.FarmVerseJwtAuthenticationConverter;
import com.htv.farmverse.service.web.ProblemResponses;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.authorization.AuthorizationEventPublisher;
import org.springframework.security.authorization.SpringAuthorizationEventPublisher;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

/**
 * Ba chuỗi filter, xét theo thứ tự:
 * <ol>
 *   <li>Endpoint chuẩn của Spring Authorization Server (/oauth2/**, /.well-known/**, /userinfo, /connect/logout).</li>
 *   <li>REST quản trị (/me, /users, /roles, /permissions, /tenants): stateless, Bearer token.</li>
 *   <li>Còn lại: trang đăng nhập (form login có chọn tenant) và actuator health.</li>
 * </ol>
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecurityConfiguration {

    static final String[] API_PATHS = {"/me", "/me/**", "/users/**", "/roles/**", "/permissions/**", "/tenants/**"};

    @Bean
    @Order(1)
    SecurityFilterChain authorizationServerChain(HttpSecurity http, TotpService totp) throws Exception {
        OAuth2AuthorizationServerConfigurer authorizationServer = new OAuth2AuthorizationServerConfigurer();
        http.securityMatcher(authorizationServer.getEndpointsMatcher())
                .with(authorizationServer, server -> server.oidc(Customizer.withDefaults()))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                // Tài khoản bật TOTP phải qua bước /login/totp trước khi được cấp code
                .addFilterAfter(new TotpEnforcementFilter(totp), SecurityContextHolderFilter.class)
                // Trình duyệt chưa đăng nhập vào /oauth2/authorize → chuyển sang trang /login
                .exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
                        new LoginUrlAuthenticationEntryPoint("/login"),
                        new MediaTypeRequestMatcher(MediaType.TEXT_HTML)))
                // /userinfo nhận access token
                .oauth2ResourceServer(resource -> resource.jwt(Customizer.withDefaults()));
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain apiChain(HttpSecurity http) throws Exception {
        http.securityMatcher(API_PATHS)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable()) // chỉ nhận Bearer token, không dùng cookie
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .oauth2ResourceServer(resource -> resource
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(new FarmVerseJwtAuthenticationConverter()))
                        // 401 và 403 trả ProblemDetail có field code, giống lỗi nghiệp vụ
                        .authenticationEntryPoint(ProblemResponses.unauthorized())
                        .accessDeniedHandler(ProblemResponses.forbidden()));
        return http.build();
    }

    @Bean
    @Order(3)
    SecurityFilterChain loginChain(HttpSecurity http, TenantAuthenticationProvider tenantAuthentication,
                                   TotpService totp, IdentityProperties properties, Clock clock) throws Exception {
        IdentityProperties.RateLimit rateLimit = properties.rateLimit();
        http.authenticationProvider(tenantAuthentication)
                // Giới hạn thử đăng nhập theo IP, đứng trước mọi filter xác thực
                .addFilterBefore(new LoginRateLimitFilter(rateLimit.maxAttempts(), rateLimit.window(), clock),
                        SecurityContextHolderFilter.class)
                // POST /login/totp: yếu tố thứ hai, gộp FACTOR_TOTP vào phiên đã đăng nhập bằng mật khẩu
                .addFilterBefore(TotpLogin.filter(totp), UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/login", "/error", "/actuator/health", "/actuator/health/**").permitAll()
                        // CSS của trang đăng nhập: người chưa đăng nhập cũng phải tải được
                        .requestMatchers("/css/**").permitAll()
                        // Tài liệu OpenAPI: endpoint chỉ tồn tại khi springdoc.*.enabled=true (dev). Prod tắt nên là 404.
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .authenticationDetailsSource(TenantLoginDetails.source())
                        .permitAll());
        return http.build();
    }

    @Bean
    TenantAuthenticationProvider tenantAuthenticationProvider(AccountUserDetailsService userDetails,
                                                              PasswordEncoder passwordEncoder,
                                                              MembershipRepository memberships,
                                                              AccountService accounts) {
        return new TenantAuthenticationProvider(userDetails, passwordEncoder, memberships, accounts);
    }

    /** {@code {bcrypt}...}: chuẩn của Spring, đổi thuật toán sau này không cần migrate. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** Phát {@code AuthorizationDeniedEvent} khi {@code @PreAuthorize} từ chối, để nhật ký bảo mật ghi lại. */
    @Bean
    AuthorizationEventPublisher authorizationEventPublisher(ApplicationEventPublisher publisher) {
        return new SpringAuthorizationEventPublisher(publisher);
    }

    // ---- Lưu trạng thái Spring Authorization Server trong PostgreSQL ----

    @Bean
    RegisteredClientRepository registeredClientRepository(JdbcTemplate jdbc) {
        return new JdbcRegisteredClientRepository(jdbc);
    }

    @Bean
    OAuth2AuthorizationService authorizationService(JdbcTemplate jdbc, RegisteredClientRepository clients) {
        return new JdbcOAuth2AuthorizationService(jdbc, clients);
    }

    @Bean
    OAuth2AuthorizationConsentService authorizationConsentService(JdbcTemplate jdbc, RegisteredClientRepository clients) {
        return new JdbcOAuth2AuthorizationConsentService(jdbc, clients);
    }

    @Bean
    AuthorizationServerSettings authorizationServerSettings(IdentityProperties properties) {
        return AuthorizationServerSettings.builder().issuer(properties.issuer()).build();
    }
}
