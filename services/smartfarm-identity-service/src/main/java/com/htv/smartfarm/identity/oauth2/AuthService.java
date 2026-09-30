package com.htv.smartfarm.identity.oauth2;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.htv.smartfarm.identity.account.application.AccountService;
import com.htv.smartfarm.identity.account.application.command.CreateAccountCommand;
import com.htv.smartfarm.identity.account.domain.UserAccountEntity;
import com.htv.smartfarm.identity.account.repository.UserAccountRepository;
import com.htv.smartfarm.identity.authorization.domain.MembershipRoleEntity;
import com.htv.smartfarm.identity.authorization.repository.MembershipRoleRepository;
import com.htv.smartfarm.identity.authorization.repository.RolePermissionRepository;
import com.htv.smartfarm.identity.authorization.repository.RoleRepository;
import com.htv.smartfarm.identity.config.IdentitySettings;
import com.htv.smartfarm.identity.tenant.domain.MembershipStatus;
import com.htv.smartfarm.identity.tenant.domain.TenantMembershipEntity;
import com.htv.smartfarm.identity.tenant.repository.TenantMembershipRepository;
import com.htv.smartfarm.identity.tenant.repository.TenantRepository;
import com.htv.smartfarm.identity.token.RefreshRepository;
import com.htv.smartfarm.identity.token.TokenService;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    public record Tokens(
            String accessToken,
            String tokenType,
            long expiresIn,
            String refreshToken
    ) {
    }

    private final UserAccountRepository accounts;
    private final TenantRepository tenants;
    private final TenantMembershipRepository memberships;
    private final MembershipRoleRepository membershipRoles;
    private final RoleRepository roles;
    private final RolePermissionRepository rolePermissions;
    private final AccountService accountService;
    private final RefreshRepository refresh;
    private final TokenService tokens;
    private final PasswordEncoder encoder;
    private final IdentitySettings settings;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final String dummyHash;

    public AuthService(
            UserAccountRepository accounts,
            TenantRepository tenants,
            TenantMembershipRepository memberships,
            MembershipRoleRepository membershipRoles,
            RoleRepository roles,
            RolePermissionRepository rolePermissions,
            AccountService accountService,
            RefreshRepository refresh,
            TokenService tokens,
            PasswordEncoder encoder,
            IdentitySettings settings,
            Clock clock
    ) {
        this.accounts = accounts;
        this.tenants = tenants;
        this.memberships = memberships;
        this.membershipRoles = membershipRoles;
        this.roles = roles;
        this.rolePermissions = rolePermissions;
        this.accountService = accountService;
        this.refresh = refresh;
        this.tokens = tokens;
        this.encoder = encoder;
        this.settings = settings;
        this.clock = clock;
        this.dummyHash = encoder.encode("dummy-password-not-for-login");
    }

    public static String email(String input) {
        if (input == null || !input.matches(
                "(?i)^[a-z0-9._%+-]{1,64}@[a-z0-9.-]{1,190}$"
        )) throw badRequest("Invalid email");
        return input.trim().toLowerCase(Locale.ROOT);
    }

    public static void password(String value) {
        if (value == null || value.length() < 12 || value.length() > 128) {
            throw badRequest("Password length must be 12..128");
        }
    }

    @Transactional
    public String register(String rawEmail, String rawPassword) {
        if (!settings.publicRegistration()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Registration disabled"
            );
        }
        password(rawPassword);
        String normalizedEmail = email(rawEmail);
        var tenant = tenants.findByCodeAndEnabledTrue(
                settings.registrationTenant()
        ).orElseThrow(() -> badRequest("Registration tenant is unavailable"));
        String userId = accountService.createAccount(new CreateAccountCommand(
                normalizedEmail,
                rawPassword,
                normalizedEmail,
                null,
                "vi-VN",
                "Asia/Ho_Chi_Minh"
        ));
        UserAccountEntity account = accounts.findById(userId).orElseThrow();
        TenantMembershipEntity membership = new TenantMembershipEntity(
                UUID.randomUUID().toString(), tenant, account
        );
        membership.activate();
        memberships.save(membership);
        roles.findByTenantIdAndCode(tenant.getId(), "USER")
                .ifPresent(role -> membershipRoles.save(
                        new MembershipRoleEntity(membership, role, userId)
                ));
        return userId;
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public Tokens login(String tenantId, String rawEmail, String rawPassword) {
        if (tenantId == null || tenantId.isBlank() || rawPassword == null) {
            throw unauthorized();
        }
        String normalizedEmail = email(rawEmail);
        UserAccountEntity account = accounts
                .findByNormalizedEmailForUpdate(normalizedEmail)
                .orElse(null);
        String hash = account == null ? dummyHash : account.getPasswordHash();
        boolean matched = encoder.matches(rawPassword, hash);
        if (account == null || !matched) {
            if (account != null) {
                account.recordLoginFailure(
                        settings.maxFailures(),
                        clock.instant().plus(settings.lockDuration())
                );
            }
            throw unauthorized();
        }
        if (!account.isLoginAllowed(clock.instant())) throw unauthorized();
        TenantMembershipEntity membership = memberships
                .findByTenantAndUserForUpdate(tenantId, account.getId())
                .orElseThrow(AuthService::unauthorized);
        if (!membership.getTenant().isEnabled()
                || membership.getStatus() != MembershipStatus.ACTIVE) {
            throw unauthorized();
        }
        account.resetLoginFailure();
        return issue(account, membership, UUID.randomUUID().toString());
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public Tokens rotate(String raw) {
        if (raw == null || raw.isBlank()) throw unauthorized();
        var session = refresh.find(raw).orElseThrow(AuthService::unauthorized);
        if (session.revoked()) {
            refresh.revokeFamily(session.familyId());
            throw unauthorized();
        }
        if (session.expiresAt() <= clock.instant().toEpochMilli()) {
            throw unauthorized();
        }
        if (!refresh.consume(session.id())) {
            refresh.revokeFamily(session.familyId());
            throw unauthorized();
        }
        UserAccountEntity account = accounts.findByIdForUpdate(session.userId())
                .orElseThrow(AuthService::unauthorized);
        if (!account.isLoginAllowed(clock.instant())) throw unauthorized();
        TenantMembershipEntity membership = memberships
                .findByTenantAndUserForUpdate(
                        session.tenantId(), session.userId()
                )
                .orElseThrow(AuthService::unauthorized);
        if (!membership.getTenant().isEnabled()
                || membership.getStatus() != MembershipStatus.ACTIVE
                || !settings.gatewayAudience().equals(session.audience())) {
            throw unauthorized();
        }
        return issue(account, membership, session.familyId());
    }

    @Transactional
    public void logout(String raw) {
        if (raw == null || raw.isBlank()) return;
        refresh.find(raw).ifPresent(session ->
                refresh.revokeFamily(session.familyId())
        );
    }

    private Tokens issue(
            UserAccountEntity account,
            TenantMembershipEntity membership,
            String familyId
    ) {
        List<String> roleIds = membershipRoles.findRoleIdsByMembershipId(
                membership.getId()
        );
        List<String> roleCodes = membershipRoles.findRoleCodesByMembershipId(
                membership.getId()
        );
        List<String> permissions = roleIds.isEmpty()
                ? List.of()
                : rolePermissions.findPermissionCodesByRoleIds(roleIds);
        String accessToken = tokens.issueUserToken(
                account.getId(),
                membership.getTenant().getId(),
                permissions,
                roleCodes,
                settings.accessTtl()
        );
        byte[] bytes = new byte[48];
        random.nextBytes(bytes);
        String rawRefresh = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
        refresh.insert(
                UUID.randomUUID().toString(),
                account.getId(),
                membership.getTenant().getId(),
                settings.gatewayAudience(),
                familyId,
                rawRefresh,
                Instant.now(clock).plus(settings.refreshTtl()).toEpochMilli()
        );
        return new Tokens(
                accessToken,
                "Bearer",
                settings.accessTtl().toSeconds(),
                rawRefresh
        );
    }

    private static ResponseStatusException unauthorized() {
        return new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Invalid credentials"
        );
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
