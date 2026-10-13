package com.htv.smartfarm.identity.authorization.application;

import java.util.List;

import com.htv.smartfarm.common.exception.ConflictException;
import com.htv.smartfarm.common.exception.NotFoundException;
import com.htv.smartfarm.common.exception.ValidationException;
import com.htv.smartfarm.identity.authorization.domain.MembershipFarmEntity;
import com.htv.smartfarm.identity.authorization.repository.MembershipFarmRepository;
import com.htv.smartfarm.identity.authorization.version.AuthorizationMutationCoordinator;
import com.htv.smartfarm.identity.tenant.domain.MembershipStatus;
import com.htv.smartfarm.identity.tenant.domain.TenantMembershipEntity;
import com.htv.smartfarm.identity.tenant.repository.TenantMembershipRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FarmScopeService {

    private final TenantMembershipRepository membershipRepository;
    private final MembershipFarmRepository membershipFarmRepository;
    private final FarmDirectoryPort farmDirectoryPort;
    private final AuthorizationMutationCoordinator authorizationMutations;

    @Transactional
    public void grantFarmAccess(
            String tenantId,
            String userId,
            String farmId
    ) {
        validateIdentifiers(
                tenantId,
                userId,
                farmId
        );

        TenantMembershipEntity membership =
                getActiveMembership(
                        tenantId,
                        userId
                );

        if (!farmDirectoryPort.existsInTenant(
                tenantId,
                farmId
        )) {
            log.error(
                    "Farm scope grant failed: farm not found, "
                            + "tenantId={}, userId={}, farmId={}",
                    tenantId,
                    userId,
                    farmId
            );

            throw NotFoundException.entity(
                    "Farm",
                    tenantId + ":" + farmId
            );
        }

        if (membershipFarmRepository
                .existsByIdMembershipIdAndIdFarmId(
                        membership.getId(),
                        farmId
                )) {
            return;
        }

        membershipFarmRepository.save(
                new MembershipFarmEntity(
                        membership,
                        farmId
                )
        );

        farmAuthorizationChanged(
                tenantId,
                userId,
                "FARM_SCOPE_GRANTED"
        );

        log.info(
                "Farm access granted: tenantId={}, userId={}, farmId={}",
                tenantId,
                userId,
                farmId
        );
    }

    @Transactional
    public void revokeFarmAccess(
            String tenantId,
            String userId,
            String farmId
    ) {
        validateIdentifiers(
                tenantId,
                userId,
                farmId
        );

        TenantMembershipEntity membership =
                getMembership(
                        tenantId,
                        userId
                );

        boolean existed =
                membershipFarmRepository
                        .existsByIdMembershipIdAndIdFarmId(
                                membership.getId(),
                                farmId
                        );

        membershipFarmRepository.deleteByMembershipIdAndFarmId(
                membership.getId(),
                farmId
        );

        if (existed) {
            farmAuthorizationChanged(
                    tenantId,
                    userId,
                    "FARM_SCOPE_REVOKED"
            );

            log.info(
                    "Farm access revoked: tenantId={}, userId={}, farmId={}",
                    tenantId,
                    userId,
                    farmId
            );
        }
    }

    @Transactional(readOnly = true)
    public List<String> getFarmIds(
            String tenantId,
            String userId
    ) {
        requireText(tenantId, "tenantId");
        requireText(userId, "userId");

        TenantMembershipEntity membership =
                getMembership(
                        tenantId,
                        userId
                );

        return membershipFarmRepository
                .findFarmIdsByMembershipId(
                        membership.getId()
                );
    }

    private TenantMembershipEntity getActiveMembership(
            String tenantId,
            String userId
    ) {
        TenantMembershipEntity membership =
                getMembership(
                        tenantId,
                        userId
                );

        if (membership.getStatus() != MembershipStatus.ACTIVE) {
            log.error(
                    "Farm scope operation failed: membership is not active, "
                            + "tenantId={}, userId={}, membershipId={}, status={}",
                    tenantId,
                    userId,
                    membership.getId(),
                    membership.getStatus()
            );

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
                .findByTenantIdAndUserId(
                        tenantId,
                        userId
                )
                .orElseThrow(() -> {
                    log.error(
                            "Tenant membership not found: "
                                    + "tenantId={}, userId={}",
                            tenantId,
                            userId
                    );

                    return NotFoundException.entity(
                            "TenantMembership",
                            tenantId + ":" + userId
                    );
                });
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

    private void requireText(
            String value,
            String field
    ) {
        if (value == null || value.isBlank()) {
            log.error(
                    "Farm scope validation failed: {} is blank",
                    field
            );

            throw new ValidationException(
                    "REQUIRED_FIELD_MISSING",
                    field + " must not be blank"
            );
        }
    }

    // Spring injects this; null only in legacy direct-construction tests.
    private void farmAuthorizationChanged(
            String tenantId,
            String userId,
            String reasonCode
    ) {
        if (authorizationMutations != null) {
            authorizationMutations.subjectChanged(
                    tenantId,
                    userId,
                    userId,
                    null,
                    reasonCode
            );
        }
    }
}