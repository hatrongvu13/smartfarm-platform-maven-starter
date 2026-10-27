package com.htv.farmverse.identity.config;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.htv.farmverse.common.contract.TokenClaimContract;
import com.htv.farmverse.identity.account.repository.UserAccountRepository;
import com.htv.farmverse.identity.account.repository.UserProfileRepository;
import com.htv.farmverse.identity.oauth2.IdentityAuthorizationPrincipal;
import com.htv.farmverse.identity.token.TokenService;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.DelegatingOAuth2TokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.JwtGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2AccessTokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2RefreshTokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IdentityOAuthProperties.class)
public class IdentityAuthorizationServerConfiguration {

    private static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(5);
    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(14);

    @Bean
    @Order(1)
    SecurityFilterChain authorizationServerSecurity(
            HttpSecurity http,
            UserAccountRepository accountRepository,
            UserProfileRepository profileRepository
    )
            throws Exception {
        OAuth2AuthorizationServerConfigurer authorizationServer =
                new OAuth2AuthorizationServerConfigurer();
        var endpoints = authorizationServer.getEndpointsMatcher();

        http
                .securityMatcher(endpoints)
                .with(authorizationServer, server -> server
                        .oidc(oidc -> oidc.userInfoEndpoint(userInfo ->
                                userInfo.userInfoMapper(context -> userInfo(
                                        context.getAuthorization().getPrincipalName(),
                                        context.getAccessToken().getScopes(),
                                        accountRepository,
                                        profileRepository
                                ))
                        ))
                )
                .authorizeHttpRequests(authorize -> authorize
                        .anyRequest().authenticated()
                )
                .csrf(csrf -> csrf.ignoringRequestMatchers(endpoints))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(
                                new LoginUrlAuthenticationEntryPoint("/oauth/login")
                        )
                );
        return http.build();
    }

    @Bean
    JWKSource<SecurityContext> identityJwkSource(TokenService tokens) {
        return tokens.jwkSource();
    }

    @Bean
    JwtEncoder authorizationServerJwtEncoder(
            JWKSource<SecurityContext> jwkSource
    ) {
        return new NimbusJwtEncoder(jwkSource);
    }

    @Bean
    RegisteredClientRepository registeredClientRepository(
            JdbcTemplate jdbcTemplate
    ) {
        return new JdbcRegisteredClientRepository(jdbcTemplate);
    }

    @Bean
    OAuth2AuthorizationService oauth2AuthorizationService(
            JdbcTemplate jdbcTemplate,
            RegisteredClientRepository clients
    ) {
        return new JdbcOAuth2AuthorizationService(jdbcTemplate, clients);
    }

    @Bean
    OAuth2AuthorizationConsentService authorizationConsentService(
            JdbcTemplate jdbcTemplate,
            RegisteredClientRepository clients
    ) {
        return new JdbcOAuth2AuthorizationConsentService(jdbcTemplate, clients);
    }

    @Bean
    AuthorizationServerSettings authorizationServerSettings(
            IdentitySettings identity
    ) {
        return AuthorizationServerSettings.builder()
                .issuer(identity.issuer())
                .build();
    }

    @Bean
    OAuth2TokenGenerator<? extends OAuth2Token> authorizationServerTokenGenerator(
            JwtEncoder encoder,
            OAuth2TokenCustomizer<JwtEncodingContext> jwtCustomizer
    ) {
        JwtGenerator jwtGenerator = new JwtGenerator(encoder);
        jwtGenerator.setJwtCustomizer(jwtCustomizer);
        return new DelegatingOAuth2TokenGenerator(
                jwtGenerator,
                new OAuth2AccessTokenGenerator(),
                new OAuth2RefreshTokenGenerator()
        );
    }

    @Bean
    OAuth2TokenCustomizer<JwtEncodingContext> identityTokenCustomizer(
            UserAccountRepository accountRepository,
            UserProfileRepository profileRepository
    ) {
        return context -> {
            boolean accessToken = org.springframework.security.oauth2.server.authorization.OAuth2TokenType
                    .ACCESS_TOKEN.equals(context.getTokenType());
            boolean idToken = OidcParameterNames.ID_TOKEN.equals(
                    context.getTokenType().getValue()
            );
            if (!accessToken && !idToken) {
                return;
            }

            if (accessToken) {
                Object configured = context.getRegisteredClient()
                        .getClientSettings()
                        .getSetting("identity.audiences");
                if (!(configured instanceof String audiencesText)
                        || audiencesText.isBlank()) {
                    throw new IllegalStateException(
                            "OAuth client must have explicit resource audience"
                    );
                }
                List<String> audiences = java.util.Arrays.stream(
                                audiencesText.split(",")
                        )
                        .map(String::trim)
                        .filter(value -> !value.isEmpty())
                        .toList();
                if (audiences.size() != 1) {
                    throw new IllegalStateException(
                            "OAuth client must be restricted to one resource audience"
                    );
                }
                context.getClaims()
                        .audience(audiences)
                        .claim(
                                TokenClaimContract.CONTRACT_VERSION_CLAIM,
                                TokenClaimContract.CURRENT_CONTRACT_VERSION
                        )
                        .claim(
                                TokenClaimContract.TOKEN_TYPE,
                                AuthorizationGrantType.CLIENT_CREDENTIALS.equals(
                                        context.getAuthorizationGrantType()
                                ) ? "service" : "user"
                        )
                        .claim(TokenClaimContract.ROLES, List.of());
            }

            IdentityAuthorizationPrincipal principal = identityPrincipal(
                    context.getPrincipal()
            );
            if (principal != null
                    && (idToken || (accessToken
                            && context.getAuthorizedScopes().contains(OidcScopes.OPENID)))) {
                var claims = context.getClaims()
                        .claim(TokenClaimContract.TENANT_ID, principal.tenantId())
                        .claim("auth_time", principal.authenticatedAt().getEpochSecond())
                        .claim("amr", principal.authenticationMethods());
                if (idToken && context.getAuthorizedScopes().contains("email")) {
                    accountRepository.findById(principal.subjectId())
                            .ifPresent(account -> claims
                                    .claim("email", account.getEmail())
                                    .claim("email_verified", account.isEmailVerified()));
                }
                if (idToken && context.getAuthorizedScopes().contains("profile")) {
                    profileRepository.findById(principal.subjectId())
                            .ifPresent(profile -> claims
                                    .claim("name", profile.getDisplayName())
                                    .claim("preferred_username",
                                            profile.getDisplayName()));
                }
            }
        };
    }

    @Bean
    ApplicationRunner provisionConfiguredOAuthClients(
            RegisteredClientRepository repository,
            PasswordEncoder passwordEncoder,
            IdentityOAuthProperties properties
    ) {
        return ignored -> {
            for (IdentityOAuthProperties.Client propertiesClient : properties.clients()) {
                RegisteredClient existing = repository.findByClientId(
                        propertiesClient.clientId()
                );
                if (existing != null) {
                    if (!passwordEncoder.matches(
                            propertiesClient.clientSecret(),
                            existing.getClientSecret()
                    )) {
                        throw new IllegalStateException(
                                "OAuth client secret changed for "
                                        + propertiesClient.clientId()
                                        + "; rotate it through the client-administration procedure"
                        );
                    }
                    continue;
                }
                repository.save(registeredClient(
                        propertiesClient,
                        passwordEncoder
                ));
            }
        };
    }

    private static RegisteredClient registeredClient(
            IdentityOAuthProperties.Client client,
            PasswordEncoder encoder
    ) {
        RegisteredClient.Builder builder = RegisteredClient
                .withId(UUID.randomUUID().toString())
                .clientId(client.clientId())
                .clientSecret(encoder.encode(client.clientSecret()))
                .clientName(client.name())
                .clientAuthenticationMethod(
                        ClientAuthenticationMethod.CLIENT_SECRET_BASIC
                )
                .clientSettings(ClientSettings.builder()
                        .requireProofKey(
                                client.grantTypes().contains("authorization_code")
                        )
                        .requireAuthorizationConsent(client.requireConsent())
                        .setting(
                                "identity.audiences",
                                String.join(",", client.audiences())
                        )
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .authorizationCodeTimeToLive(Duration.ofMinutes(3))
                        .accessTokenTimeToLive(ACCESS_TOKEN_TTL)
                        .refreshTokenTimeToLive(REFRESH_TOKEN_TTL)
                        .reuseRefreshTokens(false)
                        .idTokenSignatureAlgorithm(SignatureAlgorithm.RS256)
                        .build());

        for (String grant : client.grantTypes()) {
            builder.authorizationGrantType(grantType(grant));
        }
        for (String redirectUri : client.redirectUris()) {
            builder.redirectUri(redirectUri);
        }
        for (String redirectUri : client.postLogoutRedirectUris()) {
            builder.postLogoutRedirectUri(redirectUri);
        }
        for (String scope : client.scopes()) {
            builder.scope(scope);
        }
        return builder.build();
    }

    private static AuthorizationGrantType grantType(String grant) {
        return switch (grant) {
            case "authorization_code" -> AuthorizationGrantType.AUTHORIZATION_CODE;
            case "client_credentials" -> AuthorizationGrantType.CLIENT_CREDENTIALS;
            case "refresh_token" -> AuthorizationGrantType.REFRESH_TOKEN;
            default -> throw new IllegalArgumentException(
                    "Unsupported configured OAuth grant: " + grant
            );
        };
    }

    private static IdentityAuthorizationPrincipal identityPrincipal(
            org.springframework.security.core.Authentication authentication
    ) {
        if (authentication == null
                || !(authentication.getPrincipal()
                        instanceof IdentityAuthorizationPrincipal principal)) {
            return null;
        }
        return principal;
    }

    private static OidcUserInfo userInfo(
            String subject,
            Set<String> scopes,
            UserAccountRepository accounts,
            UserProfileRepository profiles
    ) {
        OidcUserInfo.Builder claims = OidcUserInfo.builder().subject(subject);
        accounts.findById(subject).ifPresent(account -> {
            if (scopes.contains("email")) {
                claims.email(account.getEmail())
                        .emailVerified(account.isEmailVerified());
            }
        });
        profiles.findById(subject).ifPresent(profile -> {
            if (scopes.contains("profile")) {
                claims.name(profile.getDisplayName())
                        .preferredUsername(profile.getDisplayName());
            }
        });
        return claims.build();
    }
}
