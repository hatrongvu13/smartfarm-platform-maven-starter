package com.htv.smartfarm.security.web;

import com.htv.smartfarm.security.config.SecurityProperties;
import com.htv.smartfarm.security.jwt.*;
import org.springframework.context.annotation.*;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.*;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Explicitly @Import in WebFlux gateway only; do not import ServletResourceSecurity.
 */
@Configuration(proxyBeanMethods = false)
public class ReactiveResourceSecurity {
    @Bean
    ReactiveJwtDecoder smartFarmReactiveJwtDecoder(SecurityProperties p) {
        return JwtSecurityFactory.reactiveDecoder(p);
    }

    @Bean
    SecurityWebFilterChain smartFarmReactiveChain(ServerHttpSecurity http, ReactiveJwtDecoder decoder) {
        var converter = new ReactiveJwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> reactor.core.publisher.Flux.fromIterable(
                JwtAuthorities.authorities(jwt).stream().map(value -> (GrantedAuthority) new SimpleGrantedAuthority(value)).toList()));
        return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(auth -> auth.pathMatchers("/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness").permitAll().anyExchange().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtDecoder(decoder).jwtAuthenticationConverter(converter)))
                .build();
    }
}
