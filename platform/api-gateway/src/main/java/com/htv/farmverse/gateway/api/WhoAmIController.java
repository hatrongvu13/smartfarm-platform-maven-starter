package com.htv.farmverse.gateway.api;

import java.util.Map;


import com.htv.farmverse.common.contract.TokenClaimContract;

import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1")
public class WhoAmIController {
    @GetMapping(value = "/me", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("(hasAuthority('SCOPE_farm:read') or hasAuthority('SCOPE_*'))")
    public Mono<Map<String, String>> me(@AuthenticationPrincipal Jwt jwt) {
        return Mono.just(Map.of("subject", jwt.getSubject(), "tenantId", jwt.getClaimAsString(TokenClaimContract.TENANT_ID)));
    }
}
