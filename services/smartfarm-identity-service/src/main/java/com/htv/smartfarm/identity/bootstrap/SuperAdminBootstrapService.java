package com.htv.smartfarm.identity.bootstrap;

import java.util.Locale;
import java.util.UUID;

import com.htv.smartfarm.identity.account.application.AccountService;
import com.htv.smartfarm.identity.account.application.command.CreateAccountCommand;
import com.htv.smartfarm.identity.account.domain.UserAccountEntity;
import com.htv.smartfarm.identity.account.repository.UserAccountRepository;
import com.htv.smartfarm.identity.authorization.domain.MembershipRoleEntity;
import com.htv.smartfarm.identity.authorization.domain.PermissionEntity;
import com.htv.smartfarm.identity.authorization.domain.RoleEntity;
import com.htv.smartfarm.identity.authorization.domain.RolePermissionEntity;
import com.htv.smartfarm.identity.authorization.domain.RolePermissionId;
import com.htv.smartfarm.identity.authorization.repository.MembershipRoleRepository;
import com.htv.smartfarm.identity.authorization.repository.PermissionRepository;
import com.htv.smartfarm.identity.authorization.repository.RolePermissionRepository;
import com.htv.smartfarm.identity.authorization.repository.RoleRepository;
import com.htv.smartfarm.identity.mfa.application.AuthenticatorService;
import com.htv.smartfarm.identity.mfa.application.model.TotpEnrollment;
import com.htv.smartfarm.identity.tenant.domain.TenantEntity;
import com.htv.smartfarm.identity.tenant.domain.TenantMembershipEntity;
import com.htv.smartfarm.identity.tenant.repository.TenantMembershipRepository;
import com.htv.smartfarm.identity.tenant.repository.TenantRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * One-time registration of the SYSTEM super-admin.
 *
 * <p>Replaces the old auto-create {@code DevSuperAdminBootstrap} (which generated a random
 * tenant/user id and relied on a password baked into YAML). The first super-admin is now created
 * explicitly through {@code POST /api/v1/auth/bootstrap-superadmin} and ONLY while the system has
 * no super-admin yet; a second call is rejected (409). Login then needs just email + password.</p>
 *
 * <p>The returned account carries the SUPERADMIN role AND the wildcard permission {@code *}, so its
 * access token's {@code scope} claim is {@code *} -> the gateway's {@code JwtAuthorities.isSuperAdmin}
 * ({@code SCOPE_*}) recognises it. (Verified gap: a SUPERADMIN role with no permissions issued an
 * empty-scope token the gateway did not treat as root.)</p>
 *
 * <p>TOTP is NOT required to obtain the bootstrap token (D1). Instead, a fresh TOTP enrollment is
 * prepared and its {@code otpauth://} URI is PRINTED TO THE SERVICE CONSOLE so the admin can enable
 * TOTP later from an authenticator. Nothing MFA-related gates the first token.</p>
 */
@Service
public class SuperAdminBootstrapService {

    private static final Logger log =
            LoggerFactory.getLogger(SuperAdminBootstrapService.class);

    public static final String SUPERADMIN = "SUPERADMIN";
    public static final String WILDCARD_PERMISSION = "*";
    private static final String SYSTEM_TENANT_CODE = "system";
    private static final String SYSTEM_TENANT_NAME = "System";

    private final AccountService accountService;
    private final UserAccountRepository accounts;
    private final TenantRepository tenants;
    private final TenantMembershipRepository memberships;
    private final RoleRepository roles;
    private final MembershipRoleRepository membershipRoles;
    private final PermissionRepository permissions;
    private final RolePermissionRepository rolePermissions;
    private final AuthenticatorService authenticatorService;

    public SuperAdminBootstrapService(
            AccountService accountService,
            UserAccountRepository accounts,
            TenantRepository tenants,
            TenantMembershipRepository memberships,
            RoleRepository roles,
            MembershipRoleRepository membershipRoles,
            PermissionRepository permissions,
            RolePermissionRepository rolePermissions,
            AuthenticatorService authenticatorService
    ) {
        this.accountService = accountService;
        this.accounts = accounts;
        this.tenants = tenants;
        this.memberships = memberships;
        this.roles = roles;
        this.membershipRoles = membershipRoles;
        this.permissions = permissions;
        this.rolePermissions = rolePermissions;
        this.authenticatorService = authenticatorService;
    }

    /** @return true when the system already has at least one super-admin. */
    @Transactional(readOnly = true)
    public boolean superAdminExists() {
        return membershipRoles.countByRoleCode(SUPERADMIN) > 0;
    }

    /**
     * Create the first system super-admin. The caller (AuthService) issues the token.
     *
     * @return the new user id + tenant id so the caller can mint a token.
     */
    @Transactional
    public BootstrapResult createFirstSuperAdmin(String rawEmail, String rawPassword) {
        if (superAdminExists()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A super-admin already exists; bootstrap is closed"
            );
        }
        String email = normalizeEmail(rawEmail);

        TenantEntity tenant = tenants.findByCode(SYSTEM_TENANT_CODE)
                .orElseGet(() -> tenants.save(new TenantEntity(
                        UUID.randomUUID().toString(),
                        SYSTEM_TENANT_CODE,
                        SYSTEM_TENANT_NAME
                )));

        if (accounts.existsByNormalizedEmail(email)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Email is already assigned to an account"
            );
        }

        String userId = accountService.createAccount(new CreateAccountCommand(
                email, rawPassword, "Super Admin", null, "vi-VN", "Asia/Ho_Chi_Minh"
        ));
        UserAccountEntity account = accounts.findById(userId).orElseThrow();

        TenantMembershipEntity membership = memberships.save(
                new TenantMembershipEntity(UUID.randomUUID().toString(), tenant, account)
        );
        membership.activate();

        RoleEntity role = roles.findByTenantIdAndCode(tenant.getId(), SUPERADMIN)
                .orElseGet(() -> roles.save(new RoleEntity(
                        UUID.randomUUID().toString(), tenant, SUPERADMIN,
                        "Super Administrator", true
                )));

        PermissionEntity wildcard = ensureWildcardPermission();
        if (!rolePermissions.existsById(
                new RolePermissionId(role.getId(), wildcard.getCode()))) {
            rolePermissions.save(new RolePermissionEntity(role, wildcard));
        }

        if (!membershipRoles.existsByIdMembershipIdAndIdRoleId(membership.getId(), role.getId())) {
            membershipRoles.save(new MembershipRoleEntity(membership, role, account.getId()));
        }

        log.info("System super-admin created: tenantId={}, userId={}, email={}",
                tenant.getId(), userId, email);

        // Print the optional TOTP enrollment to the console ONLY after this transaction commits,
        // so a failure in the (separate) enrollment write can never roll back the super-admin.
        final String uid = userId;
        final String mail = email;
        org.springframework.transaction.support.TransactionSynchronizationManager
                .registerSynchronization(
                        new org.springframework.transaction.support.TransactionSynchronization() {
                            @Override
                            public void afterCommit() {
                                printTotpEnrollmentToConsole(uid, mail);
                            }
                        });

        return new BootstrapResult(userId, tenant.getId());
    }

    private PermissionEntity ensureWildcardPermission() {
        return permissions.findById(WILDCARD_PERMISSION)
                .orElseGet(() -> permissions.save(new PermissionEntity(
                        WILDCARD_PERMISSION, "*", "*",
                        "Wildcard permission: grants every scope (root/super-admin)"
                )));
    }

    /**
     * Print the optional TOTP enrollment to the console. Runs in its OWN transaction
     * (REQUIRES_NEW) and is called AFTER the super-admin is committed, so a failure here can never
     * roll back the created admin. Swallows errors — TOTP is optional in D1.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void printTotpEnrollmentToConsole(String userId, String email) {
        try {
            TotpEnrollment enrollment =
                    authenticatorService.beginTotpEnrollment(userId, "SmartFarm Super Admin");
            log.warn("""

                    ================================================================
                     SUPER-ADMIN TOTP ENROLLMENT (optional -- scan to enable MFA)
                     account        : {}
                     authenticatorId: {}
                     otpauth URI    : {}
                     Scan the URI into an authenticator, then confirm via
                       POST /api/v1/auth/mfa/enrollment/confirm
                     This is printed ONCE on bootstrap and never returned over HTTP.
                    ================================================================
                    """, email, enrollment.authenticatorId(), enrollment.otpauthUri());
        } catch (RuntimeException ex) {
            log.warn("Super-admin TOTP enrollment could not be prepared ({}). "
                    + "Enable TOTP later via /api/v1/auth/mfa/enrollment/begin.", ex.getMessage());
        }
    }

    private static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email is required");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public record BootstrapResult(String userId, String tenantId) { }
}
