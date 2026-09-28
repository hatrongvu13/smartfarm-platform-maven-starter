package com.htv.smartfarm.gateway.identity;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Single public ingress for authentication and identity administration. External
 * clients never reach the identity service directly; they call the gateway, which
 * forwards to the INTERNAL identity service over HTTP (REST-to-REST — the natural
 * transport for OAuth2 token and JWKS endpoints).
 *
 * <p>Public endpoints (register/login/refresh) require no token and are forwarded
 * as-is. All other endpoints (me/verify/logout/password, and every /admin route)
 * are already enforced by the gateway's resource-server chain; the bearer token is
 * relayed downstream so identity can re-validate and apply fine-grained scopes.
 */
@RestController
public class AuthProxyController {

    private final WebClient identity;

    public AuthProxyController(WebClient identityWebClient) {
        this.identity = identityWebClient;
    }

    // ---- Auth flow (public: no gateway token required) --------------------

    @PostMapping(value = "/api/v1/auth/register", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<String>> register(@RequestBody(required = false) String body, ServerWebExchange ex) {
        return forward("/api/v1/auth/register", body, ex, false);
    }

    @PostMapping(value = "/api/v1/auth/login", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<String>> login(@RequestBody(required = false) String body, ServerWebExchange ex) {
        return forward("/api/v1/auth/login", body, ex, false);
    }

    @PostMapping(value = "/api/v1/auth/refresh", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<String>> refresh(@RequestBody(required = false) String body, ServerWebExchange ex) {
        return forward("/api/v1/auth/refresh", body, ex, false);
    }

    // ---- Authenticated auth endpoints (gateway enforces JWT, token relayed) ----

    @PostMapping(value = "/api/v1/auth/logout")
    public Mono<ResponseEntity<String>> logout(@RequestBody(required = false) String body, ServerWebExchange ex) {
        return forward("/api/v1/auth/logout", body, ex, true);
    }

    @GetMapping(value = "/api/v1/auth/me", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<String>> me(ServerWebExchange ex) {
        return forwardGet("/api/v1/auth/me", ex);
    }

    @GetMapping(value = "/api/v1/auth/verify", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<String>> verify(ServerWebExchange ex) {
        return forwardGet("/api/v1/auth/verify", ex);
    }

    @PostMapping(value = "/api/v1/auth/password")
    public Mono<ResponseEntity<String>> password(@RequestBody(required = false) String body, ServerWebExchange ex) {
        return forward("/api/v1/auth/password", body, ex, true);
    }

    // ---- Identity administration (gateway enforces JWT, token relayed) ----

    @RequestMapping(value = "/api/v1/admin/**",
            method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE})
    public Mono<ResponseEntity<String>> admin(@RequestBody(required = false) String body, ServerWebExchange ex) {
        String path = ex.getRequest().getPath().pathWithinApplication().value();
        boolean hasBody = body != null && !body.isEmpty();
        return identity.method(ex.getRequest().getMethod())
                .uri(path)
                .headers(h -> {
                    copyAuth(ex, h);
                    // Relay the client's Content-Type so identity's @RequestBody parses the
                    // body; default to JSON when a body is present but the header was dropped.
                    // Without this, POST/PUT /admin/** forwarded with no Content-Type -> 415.
                    if (hasBody) {
                        MediaType ct = ex.getRequest().getHeaders().getContentType();
                        h.setContentType(ct != null ? ct : MediaType.APPLICATION_JSON);
                    }
                })
                .bodyValue(body == null ? "" : body)
                .retrieve()
                .toEntity(String.class);
    }

    // ---- helpers ----------------------------------------------------------

    private Mono<ResponseEntity<String>> forward(String path, String body, ServerWebExchange ex, boolean relayToken) {
        return identity.post().uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(h -> { if (relayToken) copyAuth(ex, h); })
                .bodyValue(body == null ? "" : body)
                .retrieve()
                .toEntity(String.class);
    }

    private Mono<ResponseEntity<String>> forwardGet(String path, ServerWebExchange ex) {
        return identity.get().uri(path)
                .headers(h -> copyAuth(ex, h))
                .retrieve()
                .toEntity(String.class);
    }

    private void copyAuth(ServerWebExchange ex, HttpHeaders downstream) {
        String auth = ex.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (auth != null) downstream.set(HttpHeaders.AUTHORIZATION, auth);
    }
}
