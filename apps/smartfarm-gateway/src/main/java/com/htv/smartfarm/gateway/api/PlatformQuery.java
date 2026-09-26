package com.htv.smartfarm.gateway.api;

import java.util.Map;

import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Mono;

@Controller
public class PlatformQuery {
    @QueryMapping
    @PreAuthorize("hasAuthority('SCOPE_farm:read')")
    public Mono<Map<String, String>> platformStatus() {
        return ReactiveSecurityContextHolder.getContext().map(context -> {
            Jwt jwt = (Jwt) context.getAuthentication().getPrincipal();
            return Map.of("name", "SmartFarm", "status", "BOOTSTRAPPED", "tenantId", jwt.getClaimAsString("tenant_id"));
        });
    }
}
