package com.htv.smartfarm.identity.config;

import com.htv.smartfarm.identity.token.TokenService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.http.HttpMethod;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({IdentitySettings.class, ServiceTokenSettings.class})
@EnableMethodSecurity
public class IdentityConfiguration {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    JwtDecoder identityJwtDecoder(TokenService tokens) {
        return tokens.decoder();
    }

    @Bean
    SecurityFilterChain identitySecurity(HttpSecurity http, JwtDecoder decoder) throws Exception {
        http.csrf(csrf -> csrf.disable()).sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.GET, "/.well-known/jwks.json", "/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh").permitAll()
                        // Internal machine-to-machine grant: authenticates by client_id+secret and is only reachable
                        // on the loopback binding (never proxied by the gateway), so no user JWT is required here.
                        .requestMatchers(HttpMethod.POST, "/internal/service-token").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasAuthority("SCOPE_identity:admin")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o.jwt(j -> j.decoder(decoder)));
        return http.build();
    }
}
