package com.htv.smartfarm.identity.api;

import java.util.Map;

import com.htv.smartfarm.identity.oauth2.AuthService;
import com.htv.smartfarm.identity.token.TokenService;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

    private final AuthService auth;
    private final TokenService tokens;

    public AuthController(AuthService auth, TokenService tokens) {
        this.auth = auth;
        this.tokens = tokens;
    }

    public record Registration(String email, String password) { }
    public record Login(String tenantId, String email, String password) { }
    public record Refresh(String refreshToken) { }
    public record MfaVerification(
            String challengeToken,
            String method,
            String code
    ) { }
    public record TotpEnrollmentBegin(
            String challengeToken,
            String displayName
    ) { }
    public record TotpEnrollmentConfirm(
            String challengeToken,
            String authenticatorId,
            String code
    ) { }

    @GetMapping(
            value = "/.well-known/jwks.json",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<String> jwks() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .body(tokens.jwks());
    }

    @PostMapping("/api/v1/auth/register")
    public Map<String, String> register(
            @RequestBody Registration request
    ) {
        return Map.of(
                "userId",
                auth.register(request.email(), request.password())
        );
    }

    @PostMapping("/api/v1/auth/login")
    public AuthService.AuthenticationResponse login(
            @RequestBody Login request
    ) {
        return auth.login(
                request.tenantId(),
                request.email(),
                request.password()
        );
    }

    @PostMapping("/api/v1/auth/mfa/enrollment/begin")
    public com.htv.smartfarm.identity.mfa.application.model.TotpEnrollment
    beginRequiredTotpEnrollment(
            @RequestBody TotpEnrollmentBegin request
    ) {
        return auth.beginRequiredTotpEnrollment(
                request.challengeToken(),
                request.displayName()
        );
    }

    @PostMapping("/api/v1/auth/mfa/enrollment/confirm")
    public AuthService.EnrollmentConfirmationResponse
    confirmRequiredTotpEnrollment(
            @RequestBody TotpEnrollmentConfirm request
    ) {
        return auth.confirmRequiredTotpEnrollment(
                request.challengeToken(),
                request.authenticatorId(),
                request.code()
        );
    }

    @PostMapping("/api/v1/auth/mfa/verify")
    public AuthService.AuthenticationResponse verifyMfa(
            @RequestBody MfaVerification request
    ) {
        return auth.verifyMfa(
                request.challengeToken(),
                request.method(),
                request.code()
        );
    }

    @PostMapping("/api/v1/auth/refresh")
    public AuthService.Tokens refresh(
            @RequestBody Refresh request
    ) {
        return auth.rotate(request.refreshToken());
    }

    @PostMapping("/api/v1/auth/logout")
    public ResponseEntity<Void> logout(
            @RequestBody Refresh request
    ) {
        auth.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }
}
