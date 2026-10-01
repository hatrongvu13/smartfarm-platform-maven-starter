package com.htv.smartfarm.gateway.identity;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

@RestController
public class AuthProxyController {

    private final WebClient identity;

    public AuthProxyController(WebClient identityWebClient) {
        this.identity = identityWebClient;
    }

    @PostMapping(
            value = "/api/v1/auth/register",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public Mono<ResponseEntity<String>> register(
            @RequestBody(required = false) String body
    ) {
        return post("/api/v1/auth/register", body);
    }

    @PostMapping(
            value = "/api/v1/auth/login",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public Mono<ResponseEntity<String>> login(
            @RequestBody(required = false) String body
    ) {
        return post("/api/v1/auth/login", body);
    }

    @PostMapping(
            value = "/api/v1/auth/mfa/verify",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public Mono<ResponseEntity<String>> verifyMfa(
            @RequestBody(required = false) String body
    ) {
        return post("/api/v1/auth/mfa/verify", body);
    }

    @PostMapping(
            value = "/api/v1/auth/refresh",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public Mono<ResponseEntity<String>> refresh(
            @RequestBody(required = false) String body
    ) {
        return post("/api/v1/auth/refresh", body);
    }

    @PostMapping("/api/v1/auth/logout")
    public Mono<ResponseEntity<String>> logout(
            @RequestBody(required = false) String body
    ) {
        return post("/api/v1/auth/logout", body);
    }

    private Mono<ResponseEntity<String>> post(
            String path,
            String body
    ) {
        return identity.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(body == null ? "" : body)
                .exchangeToMono(response ->
                        response.toEntity(String.class)
                );
    }
}
