package com.htv.smartfarm.identity.auth;

import com.htv.smartfarm.identity.config.IdentitySettings;
import com.htv.smartfarm.identity.user.UserRepository;
import com.htv.smartfarm.identity.token.*;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class AuthService {
    public record Tokens(String accessToken, String tokenType, long expiresIn, String refreshToken) {
    }

    private final UserRepository users;
    private final RefreshRepository refresh;
    private final TokenService tokens;
    private final PasswordEncoder encoder;
    private final IdentitySettings settings;
    private final SecureRandom random = new SecureRandom();
    private final String dummyHash;

    public AuthService(UserRepository users, RefreshRepository refresh, TokenService tokens, PasswordEncoder encoder, IdentitySettings settings) {
        this.users = users;
        this.refresh = refresh;
        this.tokens = tokens;
        this.encoder = encoder;
        this.settings = settings;
        this.dummyHash = encoder.encode("dummy-password-not-for-login");
    }

    public static String email(String input) {
        if (input == null || !input.matches("(?i)^[a-z0-9._%+-]{1,64}@[a-z0-9.-]{1,190}$"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid email");
        return input.toLowerCase(Locale.ROOT);
    }

    public static void password(String value) {
        if (value == null || value.length() < 12 || value.length() > 72)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password length must be 12..72");
    }

    @Transactional
    public String register(String email, String password) {
        if (!settings.publicRegistration())
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Registration disabled");
        password(password);
        try {
            UserRepository.User user = users.create(settings.registrationTenant(), email(email), encoder.encode(password));
            users.assignRole(user.id(), "USER");
            return user.id();
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Account already exists");
        }
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public Tokens login(String tenant, String email, String password) {
        if (tenant == null || tenant.isBlank() || password == null) throw unauthorized();
        String normalized = email(email);
        var found = users.findByEmail(tenant, normalized);
        String hash = found.map(UserRepository.User::passwordHash).orElse(dummyHash);
        boolean match = encoder.matches(password, hash);
        if (found.isEmpty()) throw unauthorized();
        var user = found.get();
        long now = Instant.now().toEpochMilli();
        if (!user.enabled() || (user.lockedUntil() != null && user.lockedUntil() > now)) throw unauthorized();
        if (!match) {
            int attempts = user.failedAttempts() + 1;
            Long until = attempts >= settings.maxFailures() ? now + settings.lockDuration().toMillis() : null;
            users.failed(user, attempts, until);
            throw unauthorized();
        }
        users.resetFailures(user.id());
        return issue(user, UUID.randomUUID().toString());
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public Tokens rotate(String raw) {
        if (raw == null || raw.isBlank()) throw unauthorized();
        var session = refresh.find(raw).orElseThrow(AuthService::unauthorized);
        if (session.revoked()) {
            refresh.revokeFamily(session.familyId());
            throw unauthorized();
        }
        if (session.expiresAt() <= Instant.now().toEpochMilli()) throw unauthorized();
        var user = users.findById(session.userId()).orElseThrow(AuthService::unauthorized);
        if (!user.enabled()) throw unauthorized();
        if (!refresh.consume(session.id())) {
            refresh.revokeFamily(session.familyId());
            throw unauthorized();
        }
        return issue(user, session.familyId());
    }

    @Transactional
    public void logout(String raw) {
        if (raw == null || raw.isBlank()) return;
        refresh.find(raw).ifPresent(s -> refresh.revokeFamily(s.familyId()));
    }

    @Transactional
    public void disable(String id) {
        users.setEnabled(id, false);
        refresh.revokeUser(id);
    }

    @Transactional
    public void changePassword(String id, String oldPassword, String newPassword) {
        password(newPassword);
        var user = users.findById(id).orElseThrow(AuthService::unauthorized);
        if (!encoder.matches(oldPassword, user.passwordHash())) throw unauthorized();
        users.setPassword(id, encoder.encode(newPassword));
        refresh.revokeUser(id);
    }

    private Tokens issue(UserRepository.User user, String family) {
        byte[] bytes = new byte[48];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        refresh.insert(UUID.randomUUID().toString(), user.id(), family, raw, Instant.now().plus(settings.refreshTtl()).toEpochMilli());
        return new Tokens(tokens.issue(user, users.scopes(user.id()), users.roles(user.id())), "Bearer", settings.accessTtl().toSeconds(), raw);
    }

    private static ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
    }
}
