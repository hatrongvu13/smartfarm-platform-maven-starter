package com.htv.smartfarm.identity.authorization.application;

import java.util.List;

import com.htv.smartfarm.common.exception.ConflictException;
import com.htv.smartfarm.common.exception.NotFoundException;
import com.htv.smartfarm.common.exception.ValidationException;
import com.htv.smartfarm.identity.authorization.domain.MembershipFarmEntity;
import com.htv.smartfarm.identity.authorization.repository.MembershipFarmRepository;
import com.htv.smartfarm.identity.tenant.domain.MembershipStatus;
import com.htv.smartfarm.identity.tenant.domain.TenantMembershipEntity;
import com.htv.smartfarm.identity.tenant.repository.TenantMembershipRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FarmScopeService {

    private final TenantMembershipRepository membershipRepository;
    private final MembershipFarmRepository membershipFarmRepository;
    private final FarmDirectoryPort farmDirectoryPort;

    public FarmScopeService(
            TenantMembershipRepository membershipRepository,
            MembershipFarmRepository membershipFarmRepository,
            FarmDirectoryPort farmDirectoryPort
    ) {
        this.membershipRepository = membershipRepository;
        this.membershipFarmRepository = membershipFarmRepository;
        this.farmDirectoryPort = farmDirectoryPort;
    }

    @Transactional
    public void grantFarmAccess(
            String tenantId,
            String userId,
            String farmId
    ) {
        validateIdentifiers(tenantId, userId, farmId);

        TenantMembershipEntity membership =
                getActiveMembership(tenantId, userId);

        if (!farmDirectoryPort.existsInTenant(tenantId, farmId)) {
            throw NotFoundException.entity(
                    "Farm",
                    tenantId + ":" + farmId
            );
        }

        if (membershipFarmRepository.existsByMembershipIdAndFarmId(
                membership.getId(),
                farmId
        )) {
            return;
        }

        membershipFarmRepository.save(
                new MembershipFarmEntity(membership, farmId)
        );
    }

    @Transactional
    public void revokeFarmAccess(
            String tenantId,
            String userId,
            String farmId
    ) {
        validateIdentifiers(tenantId, userId, farmId);

        TenantMembershipEntity membership =
                getMembership(tenantId, userId);

        membershipFarmRepository.deleteByMembershipIdAndFarmId(
                membership.getId(),
                farmId
        );
    }

    @Transactional(readOnly = true)
    public List<String> getFarmIds(
            String tenantId,
            String userId
    ) {
        requireText(tenantId, "tenantId");
        requireText(userId, "userId");

        TenantMembershipEntity membership =
                getMembership(tenantId, userId);

        return membershipFarmRepository
                .findFarmIdsByMembershipId(membership.getId());
    }

    private TenantMembershipEntity getActiveMembership(
            String tenantId,
            String userId
    ) {
        TenantMembershipEntity membership =
                getMembership(tenantId, userId);

        if (membership.getStatus() != MembershipStatus.ACTIVE) {
            throw new ConflictException(
                    "MEMBERSHIP_NOT_ACTIVE",
                    "Membership is not active"
            );
        }

        return membership;
    }

    private TenantMembershipEntity getMembership(
            String tenantId,
            String userId
    ) {
        return membershipRepository
                .findByTenantIdAndUserId(tenantId, userId)
                .orElseThrow(() -> NotFoundException.entity(
                        "TenantMembership",
                        tenantId + ":" + userId
                ));
    }

    private void validateIdentifiers(
            String tenantId,
            String userId,
            String farmId
    ) {
        requireText(tenantId, "tenantId");
        requireText(userId, "userId");
        requireText(farmId, "farmId");
    }

    private void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(
                    "REQUIRED_FIELD_MISSING",
                    field + " must not be blank"
            );
        }
    }
}
