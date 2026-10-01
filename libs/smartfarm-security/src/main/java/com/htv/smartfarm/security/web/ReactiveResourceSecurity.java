package com.htv.smartfarm.security.web;

import com.htv.smartfarm.security.config.SmartFarmSecurityProperties;
import com.htv.smartfarm.security.jwt.JwtAuthorities;
import com.htv.smartfarm.security.jwt.JwtSecurityFactory;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;

import reactor.core.publisher.Flux;

@Configuration(proxyBeanMethods = false)
public class ReactiveResourceSecurity {

    @Bean
    ReactiveJwtDecoder smartFarmReactiveJwtDecoder(
            SmartFarmSecurityProperties properties
    ) {
        return JwtSecurityFactory.reactiveDecoder(
                properties.jwt()
        );
    }

    @Bean
    SecurityWebFilterChain smartFarmReactiveChain(
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
