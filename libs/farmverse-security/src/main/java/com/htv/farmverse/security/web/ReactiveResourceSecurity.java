package com.htv.farmverse.security.web;

import com.htv.farmverse.security.config.FarmVerseSecurityProperties;
import com.htv.farmverse.security.jwt.JwtAuthorities;
import com.htv.farmverse.security.jwt.JwtSecurityFactory;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;

import reactor.core.publisher.Flux;

@AutoConfiguration
@ConditionalOnClass({ServerHttpSecurity.class, ReactiveJwtDecoder.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnProperty(
        prefix = "farmverse.security.http",
        name = "enabled",
        havingValue = "true"
)
public class ReactiveResourceSecurity {

    @Bean
    ReactiveJwtDecoder smartFarmReactiveJwtDecoder(
            FarmVerseSecurityProperties properties
    ) {
        return JwtSecurityFactory.reactiveDecoder(
                properties.jwt()
        );
    }

    @Bean
    @ConditionalOnMissingBean(SecurityWebFilterChain.class)
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
