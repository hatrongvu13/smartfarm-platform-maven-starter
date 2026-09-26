package com.htv.smartfarm.gateway.config;

import com.htv.smartfarm.security.config.SecurityProperties;
import com.htv.smartfarm.security.jwt.JwtAuthorities;
import com.htv.smartfarm.security.jwt.JwtSecurityFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Flux;

/**
 * The gateway is the single public ingress and owns its own reactive security
 * chain (it does NOT import {@code ReactiveResourceSecurity}, to avoid a duplicate
 * {@link SecurityWebFilterChain}). It reuses the shared JWT decoder + authorities
 * converter, and additionally permits the public authentication endpoints
 * (register/login/refresh) which carry no token; every other exchange must be
 * authenticated here before it is proxied to an internal service.
 */
@Configuration(proxyBeanMethods = false)
@EnableReactiveMethodSecurity
public class GatewaySecurityConfiguration {

    @Bean
    SecurityProperties smartFarmSecurityProperties(Environment environment) {
        boolean devProfile = environment.acceptsProfiles(Profiles.of("dev & !prod"));
        boolean allowLocalHttp = devProfile
                && environment.getProperty("smartfarm.security.allow-local-http", Boolean.class, false);
        return new SecurityProperties(
                environment.getRequiredProperty("smartfarm.security.issuer"),
                environment.getRequiredProperty("smartfarm.security.jwk-set-uri"),
                environment.getRequiredProperty("smartfarm.security.audience"),
                allowLocalHttp);
    }

    @Bean
    ReactiveJwtDecoder smartFarmReactiveJwtDecoder(SecurityProperties properties) {
        return JwtSecurityFactory.reactiveDecoder(properties);
    }

    @Bean
    SecurityWebFilterChain gatewaySecurityChain(ServerHttpSecurity http, ReactiveJwtDecoder decoder) {
        var converter = new ReactiveJwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> Flux.fromIterable(
                JwtAuthorities.authorities(jwt).stream()
                        .map(value -> (GrantedAuthority) new SimpleGrantedAuthority(value)).toList()));

        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(auth -> auth
                        .pathMatchers("/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness").permitAll()
                        // Public auth ingress — no token; the identity service re-validates.
                        .pathMatchers(HttpMethod.POST,
                                "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh").permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtDecoder(decoder).jwtAuthenticationConverter(converter)))
                .build();
    }
}
