package com.htv.smartfarm.security.web;

import com.htv.smartfarm.security.config.SmartFarmSecurityProperties;
import com.htv.smartfarm.security.jwt.JwtAuthorities;
import com.htv.smartfarm.security.jwt.JwtSecurityFactory;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
public class ServletResourceSecurity {

    @Bean
    JwtDecoder smartFarmJwtDecoder(
            SmartFarmSecurityProperties properties
    ) {
        return JwtSecurityFactory.servletDecoder(properties.jwt());
    }

    @Bean
    SecurityFilterChain smartFarmServletChain(
            HttpSecurity http,
            JwtDecoder decoder
    ) throws Exception {
        JwtAuthenticationConverter converter =
                new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(jwt ->
                JwtAuthorities.authorities(jwt)
                        .stream()
                        .map(value ->
                                (GrantedAuthority)
                                        new SimpleGrantedAuthority(value)
                        )
                        .toList()
        );

        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/health/liveness",
                                "/actuator/health/readiness"
                        )
                        .permitAll()
                        .anyRequest()
                        .authenticated()
                )
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt
                        .decoder(decoder)
                        .jwtAuthenticationConverter(converter)
                ));

        return http.build();
    }
}
