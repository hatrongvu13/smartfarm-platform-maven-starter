package com.htv.smartfarm.identity.api;

import com.htv.smartfarm.identity.auth.AuthService;
import com.htv.smartfarm.identity.token.TokenService;
import com.htv.smartfarm.identity.user.UserRepository;

import java.util.*;

import org.springframework.http.*;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class AuthController {
    private final AuthService auth;
    private final TokenService tokens;
    private final UserRepository users;

    public AuthController(AuthService auth, TokenService tokens, UserRepository users) {
        this.auth = auth;
        this.tokens = tokens;
        this.users = users;
    }

    public record Registration(String email, String password) {
    }

    public record Login(String tenantId, String email, String password) {
    }

    public record Refresh(String refreshToken) {
    }

    public record ChangePassword(String oldPassword, String newPassword) {
    }

    @GetMapping(value = "/.well-known/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> jwks() {
        return ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(tokens.jwks());
    }

    @PostMapping("/api/v1/auth/register")
    public Map<String, String> register(@RequestBody Registration r) {
        return Map.of("userId", auth.register(r.email(), r.password()));
    }

    @PostMapping("/api/v1/auth/login")
    public AuthService.Tokens login(@RequestBody Login r) {
        return auth.login(r.tenantId(), r.email(), r.password());
    }

    @PostMapping("/api/v1/auth/refresh")
    public AuthService.Tokens refresh(@RequestBody Refresh r) {
        return auth.rotate(r.refreshToken());
    }

    @PostMapping("/api/v1/auth/logout")
    public ResponseEntity<Void> logout(@RequestBody Refresh r) {
        auth.logout(r.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/auth/me")
    public Map<String, Object> me(@AuthenticationPrincipal Jwt jwt) {
        return Map.of("userId", jwt.getSubject(), "tenantId", jwt.getClaimAsString("tenant_id"), "roles", users.roles(jwt.getSubject()), "permissions", users.scopes(jwt.getSubject()));
    }

    @GetMapping("/api/v1/auth/verify")
    public Map<String, Object> verify(@AuthenticationPrincipal Jwt jwt) {
        var user = users.findById(jwt.getSubject()).orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (!user.enabled() || !user.tenantId().equals(jwt.getClaimAsString("tenant_id")))
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return Map.of("valid", true, "subject", user.id(), "tenantId", user.tenantId());
    }

    @PostMapping("/api/v1/auth/password")
    public ResponseEntity<Void> password(@AuthenticationPrincipal Jwt jwt, @RequestBody ChangePassword r) {
        auth.changePassword(jwt.getSubject(), r.oldPassword(), r.newPassword());
        return ResponseEntity.noContent().build();
    }
}
