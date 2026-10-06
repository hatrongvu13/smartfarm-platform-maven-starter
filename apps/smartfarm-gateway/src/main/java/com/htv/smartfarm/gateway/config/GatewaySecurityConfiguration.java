package com.htv.smartfarm.gateway.config;

import com.htv.smartfarm.security.config.SmartFarmSecurityProperties;
import com.htv.smartfarm.security.jwt.JwtAuthorities;
import com.htv.smartfarm.security.jwt.JwtSecurityFactory;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;

import reactor.core.publisher.Flux;

@Configuration(proxyBeanMethods = false)
@EnableReactiveMethodSecurity
public class GatewaySecurityConfiguration {

    @Bean
    ReactiveJwtDecoder smartFarmReactiveJwtDecoder(
            SmartFarmSecurityProperties properties
    ) {
        return JwtSecurityFactory.reactiveDecoder(properties.jwt());
    }

    @Bean
    SecurityWebFilterChain gatewaySecurityChain(
            ServerHttpSecurity http,
            ReactiveJwtDecoder decoder
    ) {
        ReactiveJwtAuthenticationConverter converter =
                new ReactiveJwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(jwt ->
                Flux.fromIterable(
                        JwtAuthorities.authorities(jwt)
                                .stream()
                                .map(value ->
                                        (GrantedAuthority)
                                                new SimpleGrantedAuthority(value)
                                )
                                .toList()
                )
        );

        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(auth -> auth
                        .pathMatchers(
                                "/actuator/health",
                                "/actuator/health/liveness",
                                "/actuator/health/readiness"
                        )
                        .permitAll()
                        .pathMatchers(
                                HttpMethod.GET,
                                "/api/v1/platform/deployment-state"
                        )
                        .permitAll()
                        .pathMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/webjars/**"
                        )
                        .permitAll()
                        .pathMatchers(
                                "/graphiql",
                                "/graphiql/**"
                        )
                        .permitAll()
                        .pathMatchers("/ws/**")
                        .permitAll()
                        .pathMatchers(
                                HttpMethod.POST,
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api/v1/auth/bootstrap-superadmin",
                                "/api/v1/auth/mfa/verify",
                                "/api/v1/auth/mfa/enrollment/begin",
                                "/api/v1/auth/mfa/enrollment/confirm",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/logout"
                        )
                        .permitAll()
                        .anyExchange()
                        .authenticated()
                )
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt
                        .jwtDecoder(decoder)
                        .jwtAuthenticationConverter(converter)
                ))
                .build();
    }
}
