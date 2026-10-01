package com.htv.smartfarm.identity.bootstrap;

import java.util.Locale;
import java.util.UUID;

import com.htv.smartfarm.identity.account.application.AccountService;
import com.htv.smartfarm.identity.account.application.command.CreateAccountCommand;
import com.htv.smartfarm.identity.account.domain.UserAccountEntity;
import com.htv.smartfarm.identity.account.repository.UserAccountRepository;
import com.htv.smartfarm.identity.authorization.domain.MembershipRoleEntity;
import com.htv.smartfarm.identity.authorization.domain.RoleEntity;
import com.htv.smartfarm.identity.authorization.repository.MembershipRoleRepository;
import com.htv.smartfarm.identity.authorization.repository.RoleRepository;
import com.htv.smartfarm.identity.config.IdentitySettings;
import com.htv.smartfarm.identity.tenant.domain.TenantEntity;
import com.htv.smartfarm.identity.tenant.domain.TenantMembershipEntity;
import com.htv.smartfarm.identity.tenant.repository.TenantMembershipRepository;
import com.htv.smartfarm.identity.tenant.repository.TenantRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dev & !prod")
public class DevSuperAdminBootstrap implements ApplicationRunner {

    private static final Logger log =
            LoggerFactory.getLogger(DevSuperAdminBootstrap.class);

    private static final String DEFAULT_TENANT_CODE = "farm-demo";
    private static final String DEFAULT_TENANT_NAME = "Farm Demo";
    private static final String DEFAULT_ADMIN_EMAIL = "admin@example.test";
    private static final String SUPERADMIN = "SUPERADMIN";

    private final IdentitySettings settings;
    private final AccountService accountService;
    private final UserAccountRepository accountRepository;
    private final TenantRepository tenantRepository;
    private final TenantMembershipRepository membershipRepository;
    private final RoleRepository roleRepository;
    private final MembershipRoleRepository membershipRoleRepository;

    public DevSuperAdminBootstrap(
            IdentitySettings settings,
            AccountService accountService,
            UserAccountRepository accountRepository,
            TenantRepository tenantRepository,
            TenantMembershipRepository membershipRepository,
            RoleRepository roleRepository,
            MembershipRoleRepository membershipRoleRepository
    ) {
        this.settings = settings;
        this.accountService = accountService;
        this.accountRepository = accountRepository;
        this.tenantRepository = tenantRepository;
        this.membershipRepository = membershipRepository;
        this.roleRepository = roleRepository;
        this.membershipRoleRepository = membershipRoleRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        String password = normalize(settings.bootstrapPassword());
        if (password == null) {
            log.info("Identity development bootstrap skipped: bootstrap password is empty");
            return;
        }

        String tenantCode = defaultIfBlank(
                settings.bootstrapTenant(),
                DEFAULT_TENANT_CODE
        );
        String email = defaultIfBlank(
                settings.bootstrapEmail(),
                DEFAULT_ADMIN_EMAIL
        ).toLowerCase(Locale.ROOT);

        TenantEntity tenant = tenantRepository
                .findByCodeAndEnabledTrue(tenantCode)
                .orElseGet(() -> tenantRepository.save(
                        new TenantEntity(
                                UUID.randomUUID().toString(),
                                tenantCode,
                                DEFAULT_TENANT_NAME
                        )
                ));

        UserAccountEntity account = accountRepository
                .findByNormalizedEmail(email)
                .orElseGet(() -> {
                    String userId = accountService.createAccount(
                            new CreateAccountCommand(
                                    email,
                                    password,
                                    "Super Admin",
                                    null,
                                    "vi-VN",
                                    "Asia/Ho_Chi_Minh"
                            )
                    );
                    return accountRepository.findById(userId)
                            .orElseThrow(() -> new IllegalStateException(
                                    "Bootstrap account was not persisted"
                            ));
                });

        TenantMembershipEntity membership = membershipRepository
                .findByTenantIdAndUserId(tenant.getId(), account.getId())
                .orElseGet(() -> membershipRepository.save(
                        new TenantMembershipEntity(
                                UUID.randomUUID().toString(),
                                tenant,
                                account
                        )
                ));

        if (!membership.isActive()) {
            membership.activate();
        }

        RoleEntity role = roleRepository
                .findByTenantIdAndCode(tenant.getId(), SUPERADMIN)
                .orElseGet(() -> roleRepository.save(
                        new RoleEntity(
                                UUID.randomUUID().toString(),
                                tenant,
                                SUPERADMIN,
                                "Super Administrator",
                                true
                        )
                ));

        if (!membershipRoleRepository
                .existsByIdMembershipIdAndIdRoleId(
                        membership.getId(),
                        role.getId()
                )) {
            membershipRoleRepository.save(
                    new MembershipRoleEntity(
                            membership,
                            role,
                            account.getId()
                    )
            );
        }

        log.info(
                "Development super admin ready: tenantId={}, tenantCode={}, userId={}, email={}",
                tenant.getId(),
                tenant.getCode(),
                account.getId(),
                account.getEmail()
        );
    }

    private static String defaultIfBlank(
            String value,
            String defaultValue
    ) {
        String normalized = normalize(value);
        return normalized == null ? defaultValue : normalized;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank()
                ? null
                : value.trim();
    }
}
