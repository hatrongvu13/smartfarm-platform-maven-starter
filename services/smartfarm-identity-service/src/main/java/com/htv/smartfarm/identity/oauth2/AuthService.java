package com.htv.smartfarm.identity.oauth2;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.htv.smartfarm.identity.account.application.AccountService;
import com.htv.smartfarm.identity.account.application.PasswordPolicy;
import com.htv.smartfarm.identity.account.application.command.CreateAccountCommand;
import com.htv.smartfarm.identity.account.domain.UserAccountEntity;
import com.htv.smartfarm.identity.account.repository.UserAccountRepository;
import com.htv.smartfarm.identity.authorization.repository.MembershipRoleRepository;
import com.htv.smartfarm.identity.authorization.repository.RolePermissionRepository;
import com.htv.smartfarm.identity.authorization.repository.RoleRepository;
import com.htv.smartfarm.identity.authorization.application.RoleManagementService;
import com.htv.smartfarm.identity.authorization.version.AuthorizationMutationCoordinator;
import com.htv.smartfarm.identity.config.IdentitySettings;
import com.htv.smartfarm.identity.mfa.application.AuthenticatorService;
import com.htv.smartfarm.identity.mfa.application.MfaChallengeService;
import com.htv.smartfarm.identity.mfa.application.model.TotpEnrollment;
import com.htv.smartfarm.identity.mfa.application.model.TotpEnrollmentConfirmation;
import com.htv.smartfarm.identity.mfa.application.MfaRequirementService;
import com.htv.smartfarm.identity.mfa.domain.MfaChallengePurpose;
import com.htv.smartfarm.identity.tenant.domain.MembershipStatus;
import com.htv.smartfarm.identity.tenant.domain.TenantMembershipEntity;
import com.htv.smartfarm.identity.tenant.repository.TenantMembershipRepository;
import com.htv.smartfarm.identity.tenant.repository.TenantRepository;
import com.htv.smartfarm.identity.authorization.version.AuthorizationVersionService;
import com.htv.smartfarm.identity.token.grant.GrantType;
import com.htv.smartfarm.identity.token.grant.RefreshSessionPort;
import com.htv.smartfarm.identity.token.grant.TokenContext;
import com.htv.smartfarm.identity.token.grant.TokenGrantFacade;
import com.htv.smartfarm.identity.messaging.event.IdentityIntegrationEventPublisher;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    public static final String COMPLETED = "COMPLETED";
    public static final String MFA_REQUIRED = "MFA_REQUIRED";
    public static final String MFA_ENROLLMENT_REQUIRED =
            "MFA_ENROLLMENT_REQUIRED";
    public static final String TENANT_SELECTION_REQUIRED =
            "TENANT_SELECTION_REQUIRED";

    public record Tokens(
            String accessToken,
            String tokenType,
            long expiresIn,
            String refreshToken
    ) {
    }

    public record EnrollmentConfirmationResponse(
            String authenticatorId,
            List<String> recoveryCodes,
            AuthenticationResponse authentication
    ) {
        public EnrollmentConfirmationResponse {
            recoveryCodes = List.copyOf(recoveryCodes);
        }
    }

    public record AuthenticationResponse(
            String authenticationStatus,
            String accessToken,
            String tokenType,
            long expiresIn,
            String refreshToken,
            String challengeToken,
            List<String> availableMethods,
            long challengeExpiresIn,
            List<TenantOption> tenants
    ) {
        public AuthenticationResponse {
            availableMethods = availableMethods == null
                    ? List.of()
                    : List.copyOf(availableMethods);
            tenants = tenants == null ? List.of() : List.copyOf(tenants);
        }

        static AuthenticationResponse completed(Tokens value) {
            return new AuthenticationResponse(
                    COMPLETED,
                    value.accessToken(),
                    value.tokenType(),
                    value.expiresIn(),
                    value.refreshToken(),
                    null,
                    List.of(),
                    0,
                    List.of()
            );
        }

        static AuthenticationResponse challenge(
                String token,
                List<String> methods,
                long expiresIn
        ) {
            return new AuthenticationResponse(
                    MFA_REQUIRED,
                    null,
                    null,
                    0,
                    null,
                    token,
                    methods,
                    expiresIn,
                    List.of()
            );
        }

        static AuthenticationResponse enrollmentRequired(
                String challengeToken,
                long challengeExpiresIn
        ) {
            return new AuthenticationResponse(
                    MFA_ENROLLMENT_REQUIRED,
                    null,
                    null,
                    0,
                    null,
                    challengeToken,
                    List.of("TOTP_ENROLLMENT"),
                    challengeExpiresIn,
                    List.of()
            );
        }

        static AuthenticationResponse tenantSelectionRequired(List<TenantOption> options) {
            return new AuthenticationResponse(
                    TENANT_SELECTION_REQUIRED,
                    null, null, 0, null, null,
                    List.of(), 0,
                    options
            );
        }
    }

    public record TenantOption(String tenantId, String tenantCode, String tenantName) {
    }

    public record AuthorizationLoginResponse(
            String authenticationStatus,
            String subjectId,
            String tenantId,
            String challengeToken,
            List<String> availableMethods,
            long challengeExpiresIn,
            List<TenantOption> tenants,
            List<String> authenticationMethods,
            Instant authenticatedAt
    ) {
        public AuthorizationLoginResponse {
            availableMethods = availableMethods == null
                    ? List.of()
                    : List.copyOf(availableMethods);
            tenants = tenants == null ? List.of() : List.copyOf(tenants);
            authenticationMethods = authenticationMethods == null
                    ? List.of()
                    : List.copyOf(authenticationMethods);
        }
    }

    public record AuthorizationEnrollmentResponse(
            String authenticatorId,
            List<String> recoveryCodes,
            AuthorizationLoginResponse authentication
    ) {
        public AuthorizationEnrollmentResponse {
            recoveryCodes = List.copyOf(recoveryCodes);
        }
    }

    private record EnrollmentOutcome(
            String authenticatorId,
            List<String> recoveryCodes,
            UserAccountEntity account,
            TenantMembershipEntity membership
    ) {
    }

    private record LoginOutcome(
            String status,
            UserAccountEntity account,
            TenantMembershipEntity membership,
            String challengeToken,
            List<String> methods,
            long challengeExpiresIn,
            List<TenantOption> tenants,
            List<String> authenticationMethods
    ) {
        private static LoginOutcome completed(
                UserAccountEntity account,
                TenantMembershipEntity membership,
                List<String> methods
        ) {
            return new LoginOutcome(
                    COMPLETED, account, membership, null,
                    List.of(), 0, List.of(), methods
            );
        }
    }

    private final UserAccountRepository accounts;
    private final TenantRepository tenants;
    private final TenantMembershipRepository memberships;
    private final MembershipRoleRepository membershipRoles;
    private final RoleRepository roles;
    private final RolePermissionRepository rolePermissions;
    private final AccountService accountService;
    private final RefreshSessionPort refreshSessions;
    private final TokenGrantFacade tokenGrants;
    private final AuthorizationVersionService authorizationVersions;
    private final PasswordEncoder encoder;
    private final IdentitySettings settings;
    private final MfaRequirementService mfaRequirements;
    private final AuthenticatorService authenticatorService;
    private final MfaChallengeService mfaChallenges;
    private final Clock clock;
    private final String dummyHash;
    private RoleManagementService roleManagementService;
    private IdentityIntegrationEventPublisher events;
    private AuthorizationMutationCoordinator authorizationMutations;
    private com.htv.smartfarm.identity.bootstrap.SuperAdminBootstrapService bootstrap;

    public AuthService(
            UserAccountRepository accounts,
            TenantRepository tenants,
            TenantMembershipRepository memberships,
            MembershipRoleRepository membershipRoles,
            RoleRepository roles,
            RolePermissionRepository rolePermissions,
            AccountService accountService,
            RefreshSessionPort refreshSessions,
            TokenGrantFacade tokenGrants,
            AuthorizationVersionService authorizationVersions,
            PasswordEncoder encoder,
            IdentitySettings settings,
            MfaRequirementService mfaRequirements,
            AuthenticatorService authenticatorService,
            MfaChallengeService mfaChallenges,
            Clock clock
    ) {
        this.accounts = accounts;
        this.tenants = tenants;
        this.memberships = memberships;
        this.membershipRoles = membershipRoles;
        this.roles = roles;
        this.rolePermissions = rolePermissions;
        this.accountService = accountService;
        this.refreshSessions = refreshSessions;
        this.tokenGrants = tokenGrants;
        this.authorizationVersions = authorizationVersions;
        this.encoder = encoder;
        this.settings = settings;
        this.mfaRequirements = mfaRequirements;
        this.authenticatorService = authenticatorService;
        this.mfaChallenges = mfaChallenges;
        this.clock = clock;
        this.dummyHash = encoder.encode("dummy-password-not-for-login");
    }

    @Autowired
    void setIdentityIntegrationEventPublisher(
            IdentityIntegrationEventPublisher events
    ) {
        this.events = events;
    }

    @Autowired
    void setRoleManagementService(
            RoleManagementService roleManagementService
    ) {
        this.roleManagementService = roleManagementService;
    }

    @Autowired
    void setAuthorizationMutationCoordinator(
            AuthorizationMutationCoordinator authorizationMutations
    ) {
        this.authorizationMutations = authorizationMutations;
    }

    @Autowired
    void setSuperAdminBootstrapService(
            com.htv.smartfarm.identity.bootstrap.SuperAdminBootstrapService bootstrap
    ) {
        this.bootstrap = bootstrap;
    }

    /** Creates the first super-admin and returns a completed token grant. */
    @Transactional
    public AuthenticationResponse bootstrapSuperAdmin(String rawEmail, String rawPassword) {
        password(rawPassword);
        var result = bootstrap.createFirstSuperAdmin(rawEmail, rawPassword);
        UserAccountEntity account = accounts.findByIdForUpdate(result.userId())
                .orElseThrow(AuthService::unauthorized);
        TenantMembershipEntity membership = memberships
                .findByTenantAndUserForUpdate(result.tenantId(), result.userId())
                .orElseThrow(AuthService::unauthorized);
        account.recordLoginSuccess(clock.instant());
        return AuthenticationResponse.completed(
                issue(account, membership, GrantType.BOOTSTRAP)
        );
    }

    public static String email(String input) {
        if (input == null || !input.matches(
                "(?i)^[a-z0-9._%+-]{1,64}@[a-z0-9.-]{1,190}$"
        )) {
            throw badRequest("Invalid email");
        }
        return input.trim().toLowerCase(Locale.ROOT);
    }

    public static void password(String value) {
        if (!PasswordPolicy.hasSupportedLength(value)) {
            throw badRequest(
                    "Password must contain at least "
                            + PasswordPolicy.MINIMUM_CHARACTERS
                            + " characters and must not exceed "
                            + PasswordPolicy.MAXIMUM_UTF8_BYTES
                            + " UTF-8 bytes"
            );
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
        ).orElseThrow(() -> badRequest(
                "Registration tenant is unavailable"
        ));
        CreateAccountCommand createCommand = new CreateAccountCommand(
                normalizedEmail,
                rawPassword,
                normalizedEmail,
                null,
                "vi-VN",
                "Asia/Ho_Chi_Minh"
        );
        String userId = accountService.createAccount(
                createCommand,
                tenant.getId(),
                "self-registration",
                null
        );
        UserAccountEntity account = accounts.findById(userId).orElseThrow();
        TenantMembershipEntity membership = new TenantMembershipEntity(
                UUID.randomUUID().toString(),
                tenant,
                account
        );
        membership.activate();
        memberships.save(membership);
        roles.findByTenantIdAndCode(tenant.getId(), "USER")
                .ifPresent(role -> roleManagementService.assignRole(
                        tenant.getId(),
                        userId,
                        role.getCode(),
                        userId
                ));
        return userId;
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public AuthenticationResponse login(
            String tenantId,
            String rawEmail,
            String rawPassword
    ) {
        LoginOutcome outcome = authenticatePassword(
                tenantId,
                rawEmail,
                rawPassword
        );
        return switch (outcome.status()) {
            case TENANT_SELECTION_REQUIRED -> AuthenticationResponse
                    .tenantSelectionRequired(outcome.tenants());
            case MFA_REQUIRED -> AuthenticationResponse.challenge(
                    outcome.challengeToken(),
                    outcome.methods(),
                    outcome.challengeExpiresIn()
            );
            case MFA_ENROLLMENT_REQUIRED -> AuthenticationResponse
                    .enrollmentRequired(
                            outcome.challengeToken(),
                            outcome.challengeExpiresIn()
                    );
            case COMPLETED -> AuthenticationResponse.completed(
                    issue(
                            outcome.account(),
                            outcome.membership(),
                            GrantType.USER_LOGIN
                    )
            );
            default -> throw new IllegalStateException(
                    "Unexpected authentication status"
            );
        };
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public AuthorizationLoginResponse loginForAuthorization(
            String tenantId,
            String rawEmail,
            String rawPassword
    ) {
        LoginOutcome outcome = authenticatePassword(
                tenantId,
                rawEmail,
                rawPassword
        );
        return authorizationResponse(outcome);
    }

    private LoginOutcome authenticatePassword(
            String tenantId,
            String rawEmail,
            String rawPassword
    ) {
        if (rawPassword == null) {
            throw unauthorized();
        }
        String normalizedEmail = email(rawEmail);
        UserAccountEntity account = accounts
                .findByNormalizedEmailForUpdate(normalizedEmail)
                .orElse(null);
        String hash = account == null
                ? dummyHash
                : account.getPasswordHash();
        boolean passwordLengthSupported =
                PasswordPolicy.hasSupportedLength(rawPassword);
        boolean matched = encoder.matches(
                rawPassword,
                passwordLengthSupported ? hash : dummyHash
        ) && passwordLengthSupported;

        if (account == null || !matched) {
            if (account != null) {
                boolean wasLocked = account.getLockedUntil() != null
                        && account.getLockedUntil().isAfter(clock.instant());
                account.recordLoginFailure(
                        settings.maxFailures(),
                        clock.instant().plus(settings.lockDuration())
                );
                boolean lockedNow = !wasLocked
                        && account.getLockedUntil() != null
                        && account.getLockedUntil().isAfter(clock.instant());
                if (account.getFailedAttempts() == 1 || lockedNow) {
                    publishLoginFailure(
                            tenantId,
                            account,
                            "PASSWORD",
                            account.getFailedAttempts(),
                            lockedNow
                    );
                }
                if (lockedNow) {
                    publishAccountLocked(tenantId, account, "PASSWORD_FAILURE_THRESHOLD");
                    accountAuthorizationLocked(tenantId, account.getId());
                }
            }
            throw unauthorized();
        }

        if (!account.isLoginAllowed(clock.instant())) {
            throw unauthorized();
        }

        // Resolve the tenant from EMAIL. tenantId is optional: with a single active membership we
        // log in straight away; with several we return the choosable list; an explicit tenantId
        // (the user's follow-up choice, or a legacy caller) is honoured as before.
        String resolvedTenantId = tenantId;
        if (resolvedTenantId == null || resolvedTenantId.isBlank()) {
            List<TenantMembershipEntity> active = memberships
                    .findAllByUserIdOrderByTenantNameAsc(account.getId())
                    .stream()
                    .filter(m -> m.getStatus() == MembershipStatus.ACTIVE
                            && m.getTenant().isEnabled())
                    .toList();
            if (active.isEmpty()) {
                throw unauthorized();
            }
            if (active.size() > 1) {
                return new LoginOutcome(
                        TENANT_SELECTION_REQUIRED,
                        null,
                        null,
                        null,
                        List.of(),
                        0,
                        active.stream()
                                .map(m -> new TenantOption(
                                        m.getTenant().getId(),
                                        m.getTenant().getCode(),
                                        m.getTenant().getName()))
                                .toList(),
                        List.of()
                );
            }
            resolvedTenantId = active.get(0).getTenant().getId();
        }

        TenantMembershipEntity membership = memberships
                .findByTenantAndUserForUpdate(resolvedTenantId, account.getId())
                .orElseThrow(AuthService::unauthorized);

        if (!membership.getTenant().isEnabled()
                || membership.getStatus() != MembershipStatus.ACTIVE) {
            throw unauthorized();
        }

        final String effectiveTenantId = resolvedTenantId;

        account.resetLoginFailure();
        List<String> methods = mfaChallenges.availableMethods(account.getId());
        boolean required = mfaRequirements.requiresTotp(
                effectiveTenantId,
                account.getId()
        );

        if (!methods.isEmpty()) {
            var challenge = mfaChallenges.create(
                    account.getId(),
                    effectiveTenantId,
                    MfaChallengePurpose.LOGIN
            );
            return new LoginOutcome(
                    MFA_REQUIRED,
                    null,
                    null,
                    challenge.challengeToken(),
                    methods,
                    Math.max(
                            1,
                            challenge.expiresAt().getEpochSecond()
                                    - clock.instant().getEpochSecond()
                    ),
                    List.of(),
                    List.of()
            );
        }

        if (required) {
            var challenge = mfaChallenges.create(
                    account.getId(),
                    effectiveTenantId,
                    MfaChallengePurpose.TOTP_ENROLLMENT
            );
            return new LoginOutcome(
                    MFA_ENROLLMENT_REQUIRED,
                    null,
                    null,
                    challenge.challengeToken(),
                    List.of("TOTP_ENROLLMENT"),
                    Math.max(
                            1,
                            challenge.expiresAt().getEpochSecond()
                                    - clock.instant().getEpochSecond()
                    ),
                    List.of(),
                    List.of()
            );
        }

        account.recordLoginSuccess(clock.instant());
        publishLoginSucceeded(
                effectiveTenantId,
                account,
                "PASSWORD",
                false
        );
        return LoginOutcome.completed(account, membership, List.of("pwd"));
    }

    private AuthorizationLoginResponse authorizationResponse(
            LoginOutcome outcome
    ) {
        if (!COMPLETED.equals(outcome.status())) {
            return new AuthorizationLoginResponse(
                    outcome.status(),
                    null,
                    null,
                    outcome.challengeToken(),
                    outcome.methods(),
                    outcome.challengeExpiresIn(),
                    outcome.tenants(),
                    List.of(),
                    null
            );
        }
        return new AuthorizationLoginResponse(
                COMPLETED,
                outcome.account().getId(),
                outcome.membership().getTenant().getId(),
                null,
                List.of(),
                0,
                List.of(),
                outcome.authenticationMethods(),
                clock.instant()
        );
    }

    @Transactional
    public TotpEnrollment beginRequiredTotpEnrollment(
            String challengeToken,
            String displayName
    ) {
        var challenge = mfaChallenges.requirePending(
                challengeToken,
                MfaChallengePurpose.TOTP_ENROLLMENT
        );
        return authenticatorService.beginTotpEnrollment(
                challenge.getUserId(),
                displayName
        );
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public EnrollmentConfirmationResponse confirmRequiredTotpEnrollment(
            String challengeToken,
            String authenticatorId,
            String code
    ) {
        EnrollmentOutcome outcome = completeRequiredTotpEnrollment(
                challengeToken,
                authenticatorId,
                code
        );
        return new EnrollmentConfirmationResponse(
                outcome.authenticatorId(),
                outcome.recoveryCodes(),
                AuthenticationResponse.completed(
                        issue(
                                outcome.account(),
                                outcome.membership(),
                                GrantType.MFA_COMPLETION
                        )
                )
        );
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public AuthorizationEnrollmentResponse
    confirmRequiredTotpEnrollmentForAuthorization(
            String challengeToken,
            String authenticatorId,
            String code
    ) {
        EnrollmentOutcome outcome = completeRequiredTotpEnrollment(
                challengeToken,
                authenticatorId,
                code
        );
        return new AuthorizationEnrollmentResponse(
                outcome.authenticatorId(),
                outcome.recoveryCodes(),
                authorizationResponse(LoginOutcome.completed(
                        outcome.account(),
                        outcome.membership(),
                        List.of("pwd", "otp")
                ))
        );
    }

    private EnrollmentOutcome completeRequiredTotpEnrollment(
            String challengeToken,
            String authenticatorId,
            String code
    ) {
        try {
            var pending = mfaChallenges.requirePending(
                    challengeToken,
                    MfaChallengePurpose.TOTP_ENROLLMENT
            );
            TotpEnrollmentConfirmation confirmation =
                    authenticatorService.confirmTotpEnrollment(
                            pending.getTenantId(),
                            pending.getUserId(),
                            authenticatorId,
                            code,
                            pending.getUserId(),
                            pending.getId()
                    );
            var challenge = mfaChallenges.completeEnrollment(challengeToken);
            UserAccountEntity account = accounts.findByIdForUpdate(
                    challenge.getUserId()
            ).orElseThrow(AuthService::unauthorized);
            TenantMembershipEntity membership = memberships
                    .findByTenantAndUserForUpdate(
                            challenge.getTenantId(),
                            challenge.getUserId()
                    )
                    .orElseThrow(AuthService::unauthorized);
            if (!account.isLoginAllowed(clock.instant())
                    || !membership.getTenant().isEnabled()
                    || membership.getStatus() != MembershipStatus.ACTIVE) {
                throw unauthorized();
            }
            account.recordLoginSuccess(clock.instant());
            return new EnrollmentOutcome(
                    confirmation.authenticatorId(),
                    confirmation.recoveryCodes(),
                    account,
                    membership
            );
        } catch (IllegalArgumentException exception) {
            throw unauthorized();
        }
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public AuthenticationResponse verifyMfa(
            String challengeToken,
            String method,
            String code
    ) {
        LoginOutcome outcome = completeMfa(
                challengeToken,
                method,
                code
        );
        return AuthenticationResponse.completed(
                issue(
                        outcome.account(),
                        outcome.membership(),
                        GrantType.MFA_COMPLETION
                )
        );
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public AuthorizationLoginResponse verifyMfaForAuthorization(
            String challengeToken,
            String method,
            String code
    ) {
        return authorizationResponse(completeMfa(
                challengeToken,
                method,
                code
        ));
    }

    private LoginOutcome completeMfa(
            String challengeToken,
            String method,
            String code
    ) {
        try {
            String normalizedMethod = method == null
                    ? ""
                    : method.trim().toUpperCase(Locale.ROOT);

            switch (normalizedMethod) {
                case "TOTP" -> mfaChallenges.verifyTotp(
                        challengeToken,
                        code
                );
                case "RECOVERY_CODE" -> mfaChallenges.verifyRecoveryCode(
                        challengeToken,
                        code
                );
                default -> throw new IllegalArgumentException(
                        "Unsupported MFA method"
                );
            }

            var challenge = mfaChallenges.consume(
                    challengeToken,
                    MfaChallengePurpose.LOGIN
            );
            UserAccountEntity account = accounts.findByIdForUpdate(
                    challenge.getUserId()
            ).orElseThrow(AuthService::unauthorized);
            TenantMembershipEntity membership = memberships
                    .findByTenantAndUserForUpdate(
                            challenge.getTenantId(),
                            challenge.getUserId()
                    )
                    .orElseThrow(AuthService::unauthorized);

            if (!account.isLoginAllowed(clock.instant())
                    || !membership.getTenant().isEnabled()
                    || membership.getStatus() != MembershipStatus.ACTIVE) {
                throw unauthorized();
            }

            account.recordLoginSuccess(clock.instant());
            publishLoginSucceeded(
                    challenge.getTenantId(),
                    account,
                    normalizedMethod,
                    true
            );
            String authenticationMethod =
                    "TOTP".equals(normalizedMethod)
                            || "RECOVERY_CODE".equals(normalizedMethod)
                            ? "otp"
                            : normalizedMethod.toLowerCase(Locale.ROOT);
            return LoginOutcome.completed(
                    account,
                    membership,
                    List.of("pwd", authenticationMethod)
            );
        } catch (IllegalArgumentException exception) {
            throw unauthorized();
        }
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public Tokens rotate(String raw) {
        if (raw == null || raw.isBlank()) throw unauthorized();
        var session = refreshSessions.resolve(raw);
        UserAccountEntity account = accounts
                .findByIdForUpdate(session.subjectId())
                .orElseThrow(AuthService::unauthorized);
        if (!account.isLoginAllowed(clock.instant())) {
            throw unauthorized();
        }
        TenantMembershipEntity membership = memberships
                .findByTenantAndUserForUpdate(
                        session.tenantId(),
                        session.subjectId()
                )
                .orElseThrow(AuthService::unauthorized);
        if (!membership.getTenant().isEnabled()
                || membership.getStatus() != MembershipStatus.ACTIVE
                || !settings.gatewayAudience().equals(session.audience())) {
            refreshSessions.revokeFamily(session.familyId(), "REFRESH_CONTEXT_INVALID");
            throw unauthorized();
        }
        var context = tokenContext(
                account,
                membership,
                GrantType.REFRESH_ROTATION,
                java.util.List.of("refresh_token"),
                session.sessionId()
        );
        var grant = tokenGrants.rotate(raw, context);
        return tokens(grant);
    }

    @Transactional
    public void logout(String raw) {
        if (raw == null || raw.isBlank()) return;
        refreshSessions.revokeByRawToken(raw, "LOGOUT");
    }

    private void publishLoginSucceeded(
            String tenantId,
            UserAccountEntity account,
            String authenticationMethod,
            boolean mfaUsed
    ) {
        if (events == null || tenantId == null || tenantId.isBlank()) return;
        events.publish(
                tenantId,
                account.getId(),
                null,
                null,
                "identity.login.succeeded",
                "user-security",
                account.getId(),
                0,
                java.util.Map.of(
                        "subjectId", account.getId(),
                        "authenticationMethod", authenticationMethod,
                        "mfaUsed", mfaUsed,
                        "occurredAt", clock.instant().toString()
                )
        );
    }

    private void publishLoginFailure(
            String tenantId,
            UserAccountEntity account,
            String authenticationStage,
            int attemptCount,
            boolean locked
    ) {
        if (events == null || tenantId == null || tenantId.isBlank()) return;
        events.publish(
                tenantId,
                account.getId(),
                null,
                null,
                "identity.login.failed",
                "user-security",
                account.getId(),
                0,
                java.util.Map.of(
                        "subjectId", account.getId(),
                        "authenticationStage", authenticationStage,
                        "attemptCount", attemptCount,
                        "locked", locked
                )
        );
    }

    private void publishAccountLocked(
            String tenantId,
            UserAccountEntity account,
            String reasonCode
    ) {
        if (events == null || tenantId == null || tenantId.isBlank()) return;
        events.publish(
                tenantId,
                account.getId(),
                null,
                null,
                "identity.account.locked",
                "user-account",
                account.getId(),
                0,
                java.util.Map.of(
                        "subjectId", account.getId(),
                        "reasonCode", reasonCode,
                        "failedAttempts", account.getFailedAttempts(),
                        "lockedUntil", account.getLockedUntil().toString()
                )
        );
    }

    private Tokens issue(
            UserAccountEntity account,
            TenantMembershipEntity membership,
            GrantType grantType
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
        var versions = authorizationVersions.current(
                membership.getTenant().getId(),
                account.getId()
        );
        var context = new TokenContext(
                grantType,
                account.getId(),
                membership.getTenant().getId(),
                settings.gatewayAudience(),
                null,
                null,
                new java.util.LinkedHashSet<>(permissions),
                new java.util.LinkedHashSet<>(roleCodes),
                versions.subjectVersion(),
                versions.tenantVersion(),
                null,
                clock.instant(),
                authenticationMethods(grantType),
                settings.accessTtl()
        );
        return tokens(tokenGrants.issue(context));
    }

    private TokenContext tokenContext(
            UserAccountEntity account,
            TenantMembershipEntity membership,
            GrantType grantType,
            java.util.List<String> methods,
            String sessionId
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
        var versions = authorizationVersions.current(
                membership.getTenant().getId(),
                account.getId()
        );
        return new TokenContext(
                grantType,
                account.getId(),
                membership.getTenant().getId(),
                settings.gatewayAudience(),
                null,
                null,
                new java.util.LinkedHashSet<>(permissions),
                new java.util.LinkedHashSet<>(roleCodes),
                versions.subjectVersion(),
                versions.tenantVersion(),
                sessionId,
                clock.instant(),
                methods,
                settings.accessTtl()
        );
    }

    private java.util.List<String> authenticationMethods(GrantType grantType) {
        return switch (grantType) {
            case MFA_COMPLETION -> java.util.List.of("pwd", "otp");
            case REFRESH_ROTATION -> java.util.List.of("refresh_token");
            case BOOTSTRAP, USER_LOGIN -> java.util.List.of("pwd");
            default -> java.util.List.of();
        };
    }

    private Tokens tokens(com.htv.smartfarm.identity.token.grant.TokenGrant grant) {
        return new Tokens(
                grant.accessToken().value(),
                grant.accessToken().tokenType(),
                java.time.Duration.between(
                        clock.instant(),
                        grant.accessToken().expiresAt()
                ).toSeconds(),
                grant.refreshToken()
        );
    }

    private static ResponseStatusException unauthorized() {
        return new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Invalid credentials"
        );
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                message
        );
    }

    // DYNAMIC_AUTH_PHASE2C_ACCOUNT_LOCK: only the transition into locked state invalidates.
    private void accountAuthorizationLocked(String tenantId, String subjectId) {
        if (authorizationMutations != null
                && tenantId != null && !tenantId.isBlank()) {
            authorizationMutations.subjectChanged(
                    tenantId,
                    subjectId,
                    subjectId,
                    null,
                    "ACCOUNT_LOCKED"
            );
        }
    }

}
