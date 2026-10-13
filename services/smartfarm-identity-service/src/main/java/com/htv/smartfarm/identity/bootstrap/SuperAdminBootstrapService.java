package com.htv.smartfarm.identity.bootstrap;

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

import java.util.Locale;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

/**
 * Performs one-time registration of the SYSTEM super administrator.
 *
 * <p>This service replaces the old automatic development bootstrap, which
 * generated random tenant and user identifiers and relied on a password stored
 * in configuration. The initial super administrator is created explicitly
 * through the bootstrap endpoint and only while the system has no existing
 * SUPERADMIN membership. Subsequent bootstrap attempts are rejected.</p>
 *
 * <p>The created account receives the {@code SUPERADMIN} role and the wildcard
 * permission {@code *}. Consequently, its access token can carry the wildcard
 * scope required by the gateway to recognize it as a super administrator.</p>
 *
 * <p>TOTP enrollment is optional during the initial bootstrap. After the
 * super-administrator transaction commits successfully, a separate enrollment
 * is prepared and printed to the service console. A TOTP enrollment failure
 * cannot roll back the account that was already created.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SuperAdminBootstrapService {

    public static final String SUPERADMIN = "SUPERADMIN";
    public static final String WILDCARD_PERMISSION = "*";

    private static final String SYSTEM_TENANT_CODE = "system";
    private static final String SYSTEM_TENANT_NAME = "System";

    private static final String SUPERADMIN_DISPLAY_NAME =
            "Super Admin";

    private static final String DEFAULT_LOCALE =
            "vi-VN";

    private static final String DEFAULT_TIME_ZONE =
            "Asia/Ho_Chi_Minh";

    private static final String WILDCARD_RESOURCE_TYPE =
            "*";

    private static final String WILDCARD_ACTION =
            "*";

    private static final String WILDCARD_DESCRIPTION =
            "Wildcard permission: grants every scope (root/super-admin)";

    private static final String TOTP_ISSUER =
            "SmartFarm Super Admin";

    private final AccountService accountService;
    private final UserAccountRepository accountRepository;
    private final TenantRepository tenantRepository;
    private final TenantMembershipRepository membershipRepository;
    private final RoleRepository roleRepository;
    private final MembershipRoleRepository membershipRoleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final AuthenticatorService authenticatorService;

    /**
     * Determines whether the system already has a SUPERADMIN membership.
     *
     * @return {@code true} when at least one SUPERADMIN membership exists
     */
    @Transactional(readOnly = true)
    public boolean superAdminExists() {
        boolean exists =
                membershipRoleRepository.countByRoleCode(SUPERADMIN) > 0;

        log.debug(
                "Checked SUPERADMIN existence: exists={}",
                exists
        );

        return exists;
    }

    /**
     * Creates the first system super administrator.
     *
     * <p>The caller is responsible for issuing the initial access token after
     * this operation completes.</p>
     *
     * @param rawEmail    email supplied by the bootstrap request
     * @param rawPassword raw password supplied by the bootstrap request
     * @return identifiers required by the caller to issue a token
     */
    @Transactional
    public BootstrapResult createFirstSuperAdmin(
            String rawEmail,
            String rawPassword
    ) {
        log.info("Starting first SUPERADMIN bootstrap");

        ensureBootstrapIsOpen();

        String normalizedEmail = normalizeEmail(rawEmail);

        TenantEntity systemTenant = findOrCreateSystemTenant();

        ensureEmailIsAvailable(normalizedEmail);

        UserAccountEntity account = createSuperAdminAccount(
                normalizedEmail,
                rawPassword
        );

        TenantMembershipEntity membership = createActiveMembership(
                systemTenant,
                account
        );

        RoleEntity superAdminRole = findOrCreateSuperAdminRole(
                systemTenant
        );

        PermissionEntity wildcardPermission =
                ensureWildcardPermission();

        ensureRoleHasWildcardPermission(
                superAdminRole,
                wildcardPermission
        );

        ensureMembershipHasSuperAdminRole(
                membership,
                superAdminRole,
                account
        );

        log.info(
                "System SUPERADMIN created successfully: "
                        + "tenantId={}, userId={}",
                systemTenant.getId(),
                account.getId()
        );

        return new BootstrapResult(
                account.getId(),
                systemTenant.getId()
        );
    }

    private void ensureBootstrapIsOpen() {
        if (superAdminExists()) {
            log.warn(
                    "SUPERADMIN bootstrap rejected: "
                            + "a SUPERADMIN already exists"
            );

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A super-admin already exists; bootstrap is closed"
            );
        }
    }

    private TenantEntity findOrCreateSystemTenant() {
        return tenantRepository
                .findByCode(SYSTEM_TENANT_CODE)
                .map(tenant -> {
                    log.debug(
                            "Using existing system tenant: tenantId={}",
                            tenant.getId()
                    );

                    return tenant;
                })
                .orElseGet(this::createSystemTenant);
    }

    private TenantEntity createSystemTenant() {
        TenantEntity tenant = new TenantEntity(
                UUID.randomUUID().toString(),
                SYSTEM_TENANT_CODE,
                SYSTEM_TENANT_NAME
        );

        TenantEntity savedTenant =
                tenantRepository.save(tenant);

        log.info(
                "System tenant created during SUPERADMIN bootstrap: "
                        + "tenantId={}, tenantCode={}",
                savedTenant.getId(),
                SYSTEM_TENANT_CODE
        );

        return savedTenant;
    }

    private void ensureEmailIsAvailable(String normalizedEmail) {
        if (accountRepository.existsByNormalizedEmail(normalizedEmail)) {
            log.warn(
                    "SUPERADMIN bootstrap rejected: "
                            + "email is already assigned to an account"
            );

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Email is already assigned to an account"
            );
        }
    }

    private UserAccountEntity createSuperAdminAccount(
            String normalizedEmail,
            String rawPassword
    ) {
        log.debug("Creating account for initial SUPERADMIN");

        String userId = accountService.createAccount(
                new CreateAccountCommand(
                        normalizedEmail,
                        rawPassword,
                        SUPERADMIN_DISPLAY_NAME,
                        null,
                        DEFAULT_LOCALE,
                        DEFAULT_TIME_ZONE
                )
        );

        UserAccountEntity account = accountRepository
                .findById(userId)
                .orElseThrow(() -> {
                    log.error(
                            "SUPERADMIN account was created but could not "
                                    + "be loaded: userId={}",
                            userId
                    );

                    return new IllegalStateException(
                            "Created super-admin account could not be loaded"
                    );
                });

        log.debug(
                "Initial SUPERADMIN account created: userId={}",
                account.getId()
        );

        return account;
    }

    private TenantMembershipEntity createActiveMembership(
            TenantEntity tenant,
            UserAccountEntity account
    ) {
        TenantMembershipEntity membership =
                membershipRepository.save(
                        new TenantMembershipEntity(
                                UUID.randomUUID().toString(),
                                tenant,
                                account
                        )
                );

        membership.activate();

        log.debug(
                "Active tenant membership created for initial SUPERADMIN: "
                        + "membershipId={}, tenantId={}, userId={}",
                membership.getId(),
                tenant.getId(),
                account.getId()
        );

        return membership;
    }

    private RoleEntity findOrCreateSuperAdminRole(
            TenantEntity tenant
    ) {
        return roleRepository
                .findByTenantIdAndCode(
                        tenant.getId(),
                        SUPERADMIN
                )
                .map(role -> {
                    log.debug(
                            "Using existing SUPERADMIN role: "
                                    + "tenantId={}, roleId={}",
                            tenant.getId(),
                            role.getId()
                    );

                    return role;
                })
                .orElseGet(() -> createSuperAdminRole(tenant));
    }

    private RoleEntity createSuperAdminRole(
            TenantEntity tenant
    ) {
        RoleEntity role = new RoleEntity(
                UUID.randomUUID().toString(),
                tenant,
                SUPERADMIN,
                "Super Administrator",
                true
        );

        RoleEntity savedRole = roleRepository.save(role);

        log.info(
                "SUPERADMIN role created: tenantId={}, roleId={}",
                tenant.getId(),
                savedRole.getId()
        );

        return savedRole;
    }

    private PermissionEntity ensureWildcardPermission() {
        return permissionRepository
                .findById(WILDCARD_PERMISSION)
                .map(permission -> {
                    log.debug(
                            "Using existing wildcard permission: "
                                    + "permissionCode={}",
                            permission.getCode()
                    );

                    return permission;
                })
                .orElseGet(this::createWildcardPermission);
    }

    private PermissionEntity createWildcardPermission() {
        PermissionEntity permission = new PermissionEntity(
                WILDCARD_PERMISSION,
                WILDCARD_RESOURCE_TYPE,
                WILDCARD_ACTION,
                WILDCARD_DESCRIPTION
        );

        PermissionEntity savedPermission =
                permissionRepository.save(permission);

        log.info(
                "Wildcard permission created during SUPERADMIN bootstrap: "
                        + "permissionCode={}",
                savedPermission.getCode()
        );

        return savedPermission;
    }

    private void ensureRoleHasWildcardPermission(
            RoleEntity role,
            PermissionEntity wildcardPermission
    ) {
        RolePermissionId rolePermissionId = new RolePermissionId(
                role.getId(),
                wildcardPermission.getCode()
        );

        if (rolePermissionRepository.existsById(rolePermissionId)) {
            log.debug(
                    "SUPERADMIN role already has wildcard permission: "
                            + "roleId={}",
                    role.getId()
            );

            return;
        }

        rolePermissionRepository.save(
                new RolePermissionEntity(
                        role,
                        wildcardPermission
                )
        );

        log.info(
                "Wildcard permission granted to SUPERADMIN role: "
                        + "roleId={}, permissionCode={}",
                role.getId(),
                wildcardPermission.getCode()
        );
    }

    private void ensureMembershipHasSuperAdminRole(
            TenantMembershipEntity membership,
            RoleEntity role,
            UserAccountEntity account
    ) {
        boolean roleAlreadyAssigned =
                membershipRoleRepository
                        .existsByIdMembershipIdAndIdRoleId(
                                membership.getId(),
                                role.getId()
                        );

        if (roleAlreadyAssigned) {
            log.debug(
                    "SUPERADMIN role is already assigned to membership: "
                            + "membershipId={}, roleId={}",
                    membership.getId(),
                    role.getId()
            );

            return;
        }

        membershipRoleRepository.save(
                new MembershipRoleEntity(
                        membership,
                        role,
                        account.getId()
                )
        );

        log.info(
                "SUPERADMIN role assigned to membership: "
                        + "membershipId={}, roleId={}, userId={}",
                membership.getId(),
                role.getId(),
                account.getId()
        );
    }

    private void registerTotpEnrollmentAfterCommit(
            String userId,
            String normalizedEmail
    ) {
        if (!TransactionSynchronizationManager
                .isSynchronizationActive()) {
            log.warn(
                    "Transaction synchronization is not active; "
                            + "optional TOTP enrollment will not be prepared: "
                            + "userId={}",
                    userId
            );

            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {

                    @Override
                    public void afterCommit() {
                        printTotpEnrollmentToConsole(
                                userId,
                                normalizedEmail
                        );
                    }
                }
        );

        log.debug(
                "Registered optional TOTP enrollment after transaction commit: "
                        + "userId={}",
                userId
        );
    }

    /**
     * Prepares and prints an optional TOTP enrollment after the SUPERADMIN
     * transaction has committed.
     *
     * <p>This operation uses its own transaction so that an enrollment failure
     * cannot roll back the previously created administrator. TOTP remains
     * optional during the initial bootstrap.</p>
     *
     * <p>Warning: the generated {@code otpauth://} URI contains the TOTP secret
     * and must be treated as sensitive information. Production log storage must
     * have appropriately restricted access and retention.</p>
     *
     * @param userId identifier of the newly created administrator
     * @param email  normalized administrator email
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void printTotpEnrollmentToConsole(
            String userId,
            String email
    ) {
        try {
            TotpEnrollment enrollment =
                    authenticatorService.beginTotpEnrollment(
                            userId,
                            TOTP_ISSUER
                    );

            log.warn(
                    """
                            
                            ================================================================
                             SENSITIVE: SUPER-ADMIN TOTP ENROLLMENT
                             This URI contains a TOTP secret. Do not copy it to shared logs.
                             account        : {}
                             authenticatorId: {}
                             otpauth URI    : {}
                             Scan the URI into an authenticator, then confirm via:
                               POST /api/v1/auth/mfa/enrollment/confirm
                             This information is printed once during bootstrap.
                            ================================================================
                            """,
                    email,
                    enrollment.authenticatorId(),
                    enrollment.otpauthUri()
            );
        } catch (RuntimeException exception) {
            log.warn(
                    "Optional SUPERADMIN TOTP enrollment could not be prepared: "
                            + "userId={}, errorType={}. "
                            + "TOTP can be enabled later through the enrollment API.",
                    userId,
                    exception.getClass().getSimpleName()
            );

            log.debug(
                    "SUPERADMIN TOTP enrollment preparation failure details",
                    exception
            );
        }
    }

    private static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            log.warn(
                    "SUPERADMIN bootstrap validation failed: email is blank"
            );

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "email is required"
            );
        }

        return email.trim().toLowerCase(Locale.ROOT);
    }

    public record BootstrapResult(
            String userId,
            String tenantId
    ) {
    }
}