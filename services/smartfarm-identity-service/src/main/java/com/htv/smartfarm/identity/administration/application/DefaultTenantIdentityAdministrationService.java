package com.htv.smartfarm.identity.administration.application;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.htv.smartfarm.common.exception.ConflictException;
import com.htv.smartfarm.common.exception.NotFoundException;
import com.htv.smartfarm.common.paging.PageResult;
import com.htv.smartfarm.identity.account.application.AccountService;
import com.htv.smartfarm.identity.account.application.command.CreateAccountCommand;
import com.htv.smartfarm.identity.account.domain.UserAccountEntity;
import com.htv.smartfarm.identity.account.domain.UserProfileEntity;
import com.htv.smartfarm.identity.account.repository.UserAccountRepository;
import com.htv.smartfarm.identity.account.repository.UserProfileRepository;
import com.htv.smartfarm.identity.administration.application.command.AssignRoleCommand;
import com.htv.smartfarm.identity.administration.application.command.CreateTenantUserCommand;
import com.htv.smartfarm.identity.administration.application.command.RevokeRoleCommand;
import com.htv.smartfarm.identity.administration.application.model.AccountStatusData;
import com.htv.smartfarm.identity.administration.application.model.CreatedTenantUser;
import com.htv.smartfarm.identity.administration.application.model.RoleData;
import com.htv.smartfarm.identity.administration.application.model.RoleTypeData;
import com.htv.smartfarm.identity.administration.application.model.UserAuthorizationData;
import com.htv.smartfarm.identity.administration.application.model.UserSummaryData;
import com.htv.smartfarm.identity.authorization.application.PageSupport;
import com.htv.smartfarm.identity.authorization.application.RoleManagementService;
import com.htv.smartfarm.identity.authorization.application.model.PermissionData;
import com.htv.smartfarm.identity.authorization.domain.MembershipRoleEntity;
import com.htv.smartfarm.identity.authorization.domain.PermissionEntity;
import com.htv.smartfarm.identity.authorization.domain.RoleEntity;
import com.htv.smartfarm.identity.authorization.repository.MembershipFarmRepository;
import com.htv.smartfarm.identity.authorization.repository.MembershipRoleRepository;
import com.htv.smartfarm.identity.authorization.repository.RolePermissionRepository;
import com.htv.smartfarm.identity.authorization.repository.RoleRepository;
import com.htv.smartfarm.identity.tenant.application.TenantMembershipService;
import com.htv.smartfarm.identity.tenant.domain.MembershipStatus;
import com.htv.smartfarm.identity.tenant.domain.TenantMembershipEntity;
import com.htv.smartfarm.identity.tenant.repository.TenantMembershipRepository;
import com.htv.smartfarm.identity.token.RefreshRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DefaultTenantIdentityAdministrationService
        implements TenantIdentityAdministrationService {

    private final AccountService accountService;
    private final TenantMembershipService tenantMembershipService;
    private final RoleManagementService roleManagementService;
    private final UserAccountRepository accountRepository;
    private final UserProfileRepository profileRepository;
    private final TenantMembershipRepository membershipRepository;
    private final RoleRepository roleRepository;
    private final MembershipRoleRepository membershipRoleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final MembershipFarmRepository membershipFarmRepository;
    private final RefreshRepository refreshRepository;
    private final Clock clock;

    public DefaultTenantIdentityAdministrationService(
            AccountService accountService,
            TenantMembershipService tenantMembershipService,
            RoleManagementService roleManagementService,
            UserAccountRepository accountRepository,
            UserProfileRepository profileRepository,
            TenantMembershipRepository membershipRepository,
            RoleRepository roleRepository,
            MembershipRoleRepository membershipRoleRepository,
            RolePermissionRepository rolePermissionRepository,
            MembershipFarmRepository membershipFarmRepository,
            RefreshRepository refreshRepository,
            Clock clock
    ) {
        this.accountService = accountService;
        this.tenantMembershipService = tenantMembershipService;
        this.roleManagementService = roleManagementService;
        this.accountRepository = accountRepository;
        this.profileRepository = profileRepository;
        this.membershipRepository = membershipRepository;
        this.roleRepository = roleRepository;
        this.membershipRoleRepository = membershipRoleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.membershipFarmRepository = membershipFarmRepository;
        this.refreshRepository = refreshRepository;
        this.clock = clock;
    }

    @Override
    public PageResult<RoleData> listRoles(String tenantId, int pageSize, String pageToken, boolean includePermissions) {
        List<RoleData> roles = roleRepository.findAllByTenantIdOrderByCodeAsc(tenantId).stream()
                .map(role -> toRoleData(role, includePermissions))
                .toList();
        return PageSupport.page(roles, pageSize, pageToken);
    }

    @Override
    public RoleData getRole(String tenantId, String roleId, String roleCode) {
        RoleEntity role;
        if (roleId != null && !roleId.isBlank()) {
            role = roleRepository.findById(roleId)
                    .filter(value -> value.getTenant().getId().equals(tenantId))
                    .orElseThrow(() -> NotFoundException.entity("Role", tenantId + ":" + roleId));
        } else {
            String code = requireText(roleCode, "roleCode").toUpperCase(Locale.ROOT);
            role = roleRepository.findByTenantIdAndCode(tenantId, code)
                    .orElseThrow(() -> NotFoundException.entity("Role", tenantId + ":" + code));
        }
        return toRoleData(role, true);
    }

    @Override
    public PageResult<UserSummaryData> listUsers(String tenantId, int pageSize, String pageToken,
            String searchText, MembershipStatus membershipStatus, String roleCode) {
        String search = searchText == null ? "" : searchText.trim().toLowerCase(Locale.ROOT);
        String normalizedRole = roleCode == null ? "" : roleCode.trim().toUpperCase(Locale.ROOT);
        List<UserSummaryData> users = membershipRepository.findAll().stream()
                .filter(m -> m.getTenant().getId().equals(tenantId))
                .filter(m -> membershipStatus == null || m.getStatus() == membershipStatus)
                .map(this::toUserSummary)
                .filter(u -> search.isBlank()
                        || u.email().toLowerCase(Locale.ROOT).contains(search)
                        || u.displayName().toLowerCase(Locale.ROOT).contains(search))
                .filter(u -> normalizedRole.isBlank() || u.roles().contains(normalizedRole))
                .sorted(Comparator.comparing(UserSummaryData::email))
                .toList();
        return PageSupport.page(users, pageSize, pageToken);
    }

    @Override
    public UserAuthorizationData getUserAuthorization(String tenantId, String subjectId) {
        TenantMembershipEntity membership = membershipRepository.findByTenantIdAndUserId(tenantId, subjectId)
                .orElseThrow(() -> NotFoundException.entity("TenantMembership", tenantId + ":" + subjectId));
        return toAuthorization(membership);
    }

    @Override
    @Transactional
    public CreatedTenantUser createUser(CreateTenantUserCommand command) {
        requireCommand(command);
        String normalizedEmail = command.email().trim().toLowerCase(Locale.ROOT);
        UserAccountEntity account = accountRepository.findByNormalizedEmail(normalizedEmail).orElse(null);
        boolean existing = account != null;
        String subjectId;
        if (existing) {
            subjectId = account.getId();
        } else {
            subjectId = accountService.createAccount(
                    new CreateAccountCommand(
                            command.email(), command.initialPassword(), command.displayName(), command.phoneNumber(),
                            command.locale(), command.timeZone()),
                    command.tenantId(),
                    command.actorId(),
                    null
            );
        }
        if (membershipRepository.existsByTenantIdAndUserId(command.tenantId(), subjectId)) {
            throw new ConflictException("TENANT_MEMBERSHIP_ALREADY_EXISTS", "User is already a member of this tenant");
        }
        tenantMembershipService.inviteUser(
                command.tenantId(), subjectId, command.actorId(), null);
        if (command.activateImmediately()) {
            tenantMembershipService.activateMembership(
                    command.tenantId(), subjectId, command.actorId(), null, "Activated during user creation");
        }
        TenantMembershipEntity membership = membershipRepository.findByTenantAndUserForUpdate(command.tenantId(), subjectId)
                .orElseThrow(() -> NotFoundException.entity("TenantMembership", command.tenantId() + ":" + subjectId));
        for (String codeValue : new LinkedHashSet<>(command.initialRoleCodes())) {
            String code = requireText(codeValue, "initialRoleCode").toUpperCase(Locale.ROOT);
            rejectPlatformRole(code);
            RoleEntity role = roleRepository.findByTenantIdAndCode(command.tenantId(), code)
                    .orElseThrow(() -> NotFoundException.entity("Role", command.tenantId() + ":" + code));
            if (!membershipRoleRepository.existsByIdMembershipIdAndIdRoleId(membership.getId(), role.getId())) {
                membershipRoleRepository.save(new MembershipRoleEntity(membership, role, command.actorId()));
            }
        }
        return new CreatedTenantUser(subjectId, membership.getId(), command.tenantId(), membership.getStatus(), existing);
    }

    @Override
    @Transactional
    public UserAuthorizationData assignRole(AssignRoleCommand command) {
        roleManagementService.assignRole(command.tenantId(), command.subjectId(), command.roleCode(), command.actorId());
        return getUserAuthorization(command.tenantId(), command.subjectId());
    }

    @Override
    @Transactional
    public UserAuthorizationData revokeRole(RevokeRoleCommand command) {
        roleManagementService.revokeRole(command.tenantId(), command.subjectId(), command.roleCode());
        return getUserAuthorization(command.tenantId(), command.subjectId());
    }

    @Override
    @Transactional
    public UserAuthorizationData disableMembership(String tenantId, String subjectId, String actorId, String reason) {
        tenantMembershipService.disableMembership(tenantId, subjectId, actorId, null, reason);
        refreshRepository.revokeUserInTenant(subjectId, tenantId);
        return getUserAuthorization(tenantId, subjectId);
    }

    @Override
    @Transactional
    public UserAuthorizationData enableMembership(String tenantId, String subjectId, String actorId) {
        tenantMembershipService.activateMembership(tenantId, subjectId, actorId, null, "Enabled by administrator");
        return getUserAuthorization(tenantId, subjectId);
    }

    @Override
    @Transactional
    public UserAuthorizationData suspendMembership(String tenantId, String subjectId, String actorId, String reason) {
        tenantMembershipService.suspendMembership(tenantId, subjectId, actorId, null, reason);
        refreshRepository.revokeUserInTenant(subjectId, tenantId);
        return getUserAuthorization(tenantId, subjectId);
    }

    @Override
    @Transactional
    public UserAuthorizationData disableAccount(String tenantId, String subjectId, String actorId) {
        requireMembership(tenantId, subjectId);
        accountService.disableAccount(subjectId, tenantId, actorId, null);
        return getUserAuthorization(tenantId, subjectId);
    }

    @Override
    @Transactional
    public UserAuthorizationData enableAccount(String tenantId, String subjectId, String actorId) {
        requireMembership(tenantId, subjectId);
        accountService.enableAccount(subjectId, tenantId, actorId, null);
        return getUserAuthorization(tenantId, subjectId);
    }

    @Override
    @Transactional
    public UserAuthorizationData unlockAccount(String tenantId, String subjectId, String actorId) {
        requireMembership(tenantId, subjectId);
        accountService.unlockAccount(subjectId, tenantId, actorId, null);
        return getUserAuthorization(tenantId, subjectId);
    }

    private void requireMembership(String tenantId, String subjectId) {
        if (!membershipRepository.existsByTenantIdAndUserId(tenantId, subjectId)) {
            throw NotFoundException.entity("TenantMembership", tenantId + ":" + subjectId);
        }
    }

    private UserSummaryData toUserSummary(TenantMembershipEntity membership) {
        UserAccountEntity account = membership.getUser();
        UserProfileEntity profile = profileRepository.findWithAccountByUserId(account.getId())
                .orElseThrow(() -> NotFoundException.entity("UserProfile", account.getId()));
        return new UserSummaryData(account.getId(), membership.getId(), membership.getTenant().getId(),
                account.getEmail(), profile.getDisplayName(), accountStatus(account), membership.getStatus(),
                membershipRoleRepository.findRoleCodesByMembershipId(membership.getId()),
                account.getCreatedAt(), membership.getUpdatedAt());
    }

    private UserAuthorizationData toAuthorization(TenantMembershipEntity membership) {
        UserAccountEntity account = membership.getUser();
        List<RoleData> roles = membershipRoleRepository.findAllWithRoleByMembershipId(membership.getId()).stream()
                .map(MembershipRoleEntity::getRole).map(role -> toRoleData(role, true)).toList();
        List<PermissionData> permissions = roles.stream().flatMap(role -> role.permissions().stream())
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toMap(PermissionData::code, p -> p, (a, b) -> a, java.util.LinkedHashMap::new),
                        map -> List.copyOf(map.values())));
        return new UserAuthorizationData(account.getId(), membership.getId(), membership.getTenant().getId(),
                accountStatus(account), membership.getStatus(), roles, permissions,
                membershipFarmRepository.findFarmIdsByMembershipId(membership.getId()), membership.getUpdatedAt());
    }

    private RoleData toRoleData(RoleEntity role, boolean includePermissions) {
        List<PermissionData> permissions = includePermissions
                ? rolePermissionRepository.findAllByIdRoleId(role.getId()).stream()
                        .map(value -> toPermissionData(value.getPermission())).toList()
                : List.of();
        RoleTypeData type = isPlatformCode(role.getCode()) ? RoleTypeData.PLATFORM
                : role.isSystemRole() ? RoleTypeData.SYSTEM : RoleTypeData.TENANT;
        return new RoleData(role.getId(), role.getTenant().getId(), role.getCode(), role.getName(), type,
                permissions, role.getCreatedAt(), role.getUpdatedAt(), role.getVersion());
    }

    private PermissionData toPermissionData(PermissionEntity permission) {
        return new PermissionData(permission.getCode(), permission.getResourceType(),
                permission.getAction(), permission.getDescription());
    }

    private AccountStatusData accountStatus(UserAccountEntity account) {
        if (!account.isEnabled()) return AccountStatusData.DISABLED;
        if (account.getLockedUntil() != null && account.getLockedUntil().isAfter(clock.instant())) return AccountStatusData.LOCKED;
        return AccountStatusData.ACTIVE;
    }

    private static boolean isPlatformCode(String code) {
        return "PLATFORM_ADMIN".equals(code) || "SUPERADMIN".equals(code);
    }

    private static void rejectPlatformRole(String code) {
        if (isPlatformCode(code)) throw new ConflictException("PLATFORM_ROLE_DELEGATION_NOT_ALLOWED",
                "Platform roles cannot be delegated through tenant administration");
    }

    private static void requireCommand(CreateTenantUserCommand command) {
        if (command == null) throw new IllegalArgumentException("command must not be null");
        requireText(command.tenantId(), "tenantId"); requireText(command.actorId(), "actorId");
        requireText(command.email(), "email"); requireText(command.displayName(), "displayName");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
}
