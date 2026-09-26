package com.htv.smartfarm.readiness.config;

import com.htv.smartfarm.security.grpc.GrpcMethodPolicy;

import java.util.Map;
import java.util.Set;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
public class ReadinessSecurityConfiguration {
    @Bean
    GrpcMethodPolicy readinessMethodPolicy() {
        return new GrpcMethodPolicy(Map.of(
                "smartfarm.readiness.v1.PlatformReadinessService/GetPlatformReadiness", "SCOPE_platform:read"),
                Set.of("grpc.health.v1.Health/Check", "grpc.health.v1.Health/Watch"));
    }

    @Bean
    SecurityFilterChain readinessHttpSecurity(HttpSecurity http, JwtDecoder decoder) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/platform/readiness").hasAuthority("SCOPE_platform:read")
                        .anyRequest().denyAll())
                .oauth2ResourceServer(o -> o.jwt(jwt -> jwt.decoder(decoder)));
        return http.build();
    }
}
