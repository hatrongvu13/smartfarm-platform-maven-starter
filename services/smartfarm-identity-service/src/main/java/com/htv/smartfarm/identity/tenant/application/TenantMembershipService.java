package com.htv.smartfarm.identity.tenant.application;

import java.util.List;
import java.util.UUID;

import com.htv.smartfarm.common.exception.ConflictException;
import com.htv.smartfarm.common.exception.NotFoundException;
import com.htv.smartfarm.identity.account.domain.UserAccountEntity;
import com.htv.smartfarm.identity.account.repository.UserAccountRepository;
import com.htv.smartfarm.identity.authorization.repository.MembershipFarmRepository;
import com.htv.smartfarm.identity.authorization.repository.MembershipRoleRepository;
import com.htv.smartfarm.identity.tenant.application.model.TenantMembershipData;
import com.htv.smartfarm.identity.tenant.domain.TenantEntity;
import com.htv.smartfarm.identity.tenant.domain.TenantMembershipEntity;
import com.htv.smartfarm.identity.tenant.repository.TenantMembershipRepository;
import com.htv.smartfarm.identity.tenant.repository.TenantRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantMembershipService {

    private final TenantRepository tenantRepository;
    private final UserAccountRepository userAccountRepository;
    private final TenantMembershipRepository membershipRepository;
    private final MembershipRoleRepository membershipRoleRepository;
    private final MembershipFarmRepository membershipFarmRepository;

    public TenantMembershipService(
            TenantRepository tenantRepository,
            UserAccountRepository userAccountRepository,
            TenantMembershipRepository membershipRepository,
            MembershipRoleRepository membershipRoleRepository,
            MembershipFarmRepository membershipFarmRepository
    ) {
        this.tenantRepository = tenantRepository;
        this.userAccountRepository = userAccountRepository;
        this.membershipRepository = membershipRepository;
        this.membershipRoleRepository = membershipRoleRepository;
        this.membershipFarmRepository = membershipFarmRepository;
    }

    @Transactional
    public String inviteUser(
            String tenantId,
            String userId
    ) {
        requireText(tenantId, "tenantId");
        requireText(userId, "userId");

        TenantEntity tenant = getEnabledTenant(tenantId);
        UserAccountEntity user = getUser(userId);

        if (membershipRepository.existsByTenantIdAndUserId(
                tenantId,
                userId
        )) {
            throw new ConflictException(
                    "TENANT_MEMBERSHIP_ALREADY_EXISTS",
                    "User is already a member of this tenant"
            );
        }

        TenantMembershipEntity membership =
                new TenantMembershipEntity(
                        UUID.randomUUID().toString(),
                        tenant,
                        user
                );

        membershipRepository.save(membership);
        return membership.getId();
    }

    @Transactional
    public void activateMembership(
            String tenantId,
            String userId
    ) {
        TenantMembershipEntity membership =
                getMembershipForUpdate(tenantId, userId);

        membership.activate();
    }

    @Transactional
    public void suspendMembership(
            String tenantId,
            String userId
    ) {
        TenantMembershipEntity membership =
                getMembershipForUpdate(tenantId, userId);

        membership.suspend();
    }

    @Transactional
    public void disableMembership(
            String tenantId,
            String userId
    ) {
        TenantMembershipEntity membership =
                getMembershipForUpdate(tenantId, userId);

        membership.disable();
    }

    @Transactional(readOnly = true)
    public TenantMembershipData getMembership(
            String tenantId,
            String userId
    ) {
        requireText(tenantId, "tenantId");
        requireText(userId, "userId");

        TenantMembershipEntity membership = membershipRepository
                .findByTenantIdAndUserId(tenantId, userId)
                .orElseThrow(() -> NotFoundException.entity(
                        "TenantMembership",
                        tenantId + ":" + userId
                ));

        return toData(membership);
    }

    @Transactional(readOnly = true)
    public List<TenantMembershipData> findUserMemberships(
            String userId
    ) {
        requireText(userId, "userId");

        return membershipRepository
                .findAllByUserIdOrderByTenantNameAsc(userId)
                .stream()
                .map(this::toData)
                .toList();
    }

    private TenantMembershipData toData(
            TenantMembershipEntity membership
    ) {
        List<String> roles = membershipRoleRepository
                .findRoleCodesByMembershipId(
                        membership.getId()
                );

        List<String> farms = membershipFarmRepository
                .findFarmIdsByMembershipId(
                        membership.getId()
                );

        return new TenantMembershipData(
                membership.getId(),
                membership.getTenant().getId(),
                membership.getStatus(),
                roles,
                farms
        );
    }

    private TenantMembershipEntity getMembershipForUpdate(
            String tenantId,
            String userId
    ) {
        requireText(tenantId, "tenantId");
        requireText(userId, "userId");

        return membershipRepository
                .findByTenantAndUserForUpdate(tenantId, userId)
                .orElseThrow(() -> NotFoundException.entity(
                        "TenantMembership",
                        tenantId + ":" + userId
                ));
    }

    private TenantEntity getEnabledTenant(String tenantId) {
        TenantEntity tenant = tenantRepository
                .findById(tenantId)
                .orElseThrow(() -> NotFoundException.entity(
                        "Tenant",
                        tenantId
                ));

        if (!tenant.isEnabled()) {
            throw new ConflictException(
                    "TENANT_DISABLED",
                    "Tenant is disabled"
            );
        }

        return tenant;
    }

    private UserAccountEntity getUser(String userId) {
        return userAccountRepository
                .findById(userId)
                .orElseThrow(() -> NotFoundException.entity(
                        "UserAccount",
                        userId
                ));
    }

    private void requireText(
            String value,
            String field
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    field + " must not be blank"
            );
        }
    }
}
