package com.htv.farmverse.identity.config;

import java.time.Clock;

import com.htv.farmverse.identity.account.AccountService;
import com.htv.farmverse.identity.login.AccountUserDetailsService;
import com.htv.farmverse.identity.login.TenantAuthenticationProvider;
import com.htv.farmverse.identity.login.TenantLoginDetails;
import com.htv.farmverse.identity.tenant.MembershipRepository;
import com.htv.farmverse.service.security.FarmVerseJwtAuthenticationConverter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
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
    SecurityFilterChain authorizationServerChain(HttpSecurity http) throws Exception {
        OAuth2AuthorizationServerConfigurer authorizationServer = new OAuth2AuthorizationServerConfigurer();
        http.securityMatcher(authorizationServer.getEndpointsMatcher())
                .with(authorizationServer, server -> server.oidc(Customizer.withDefaults()))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
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
                .oauth2ResourceServer(resource -> resource.jwt(jwt ->
                        jwt.jwtAuthenticationConverter(new FarmVerseJwtAuthenticationConverter())));
        return http.build();
    }

    @Bean
    @Order(3)
    SecurityFilterChain loginChain(HttpSecurity http, TenantAuthenticationProvider tenantAuthentication) throws Exception {
        http.authenticationProvider(tenantAuthentication)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/login", "/error", "/actuator/health", "/actuator/health/**").permitAll()
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
