package com.htv.farmverse.identity.token;

import java.time.Clock;

import com.htv.farmverse.identity.account.repository.UserAccountRepository;
import com.htv.farmverse.identity.authorization.version.AuthorizationVersionService;
import com.htv.farmverse.identity.authorization.version.AuthorizationVersionTuple;
import com.htv.farmverse.identity.tenant.domain.MembershipStatus;
import com.htv.farmverse.identity.tenant.repository.TenantMembershipRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Revalidates actor state before Identity signs a delegated service token. */
@Service
public class DelegatedActorVerificationService {
    private final UserAccountRepository accounts;
    private final TenantMembershipRepository memberships;
    private final AuthorizationVersionService versions;
    private final Clock clock;

    public DelegatedActorVerificationService(
            UserAccountRepository accounts,
            TenantMembershipRepository memberships,
            AuthorizationVersionService versions,
            Clock clock
    ) {
        this.accounts = accounts;
        this.memberships = memberships;
        this.versions = versions;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AuthorizationVersionTuple verify(String tenantId, String actorSubjectId) {
        var account = accounts.findById(actorSubjectId)
                .orElseThrow(() -> new SecurityException("delegated actor account not found"));
        if (!account.isEnabled() || !account.isLoginAllowed(clock.instant())) {
            throw new SecurityException("delegated actor account is not active");
        }
        var membership = memberships.findByTenantIdAndUserId(tenantId, actorSubjectId)
                .orElseThrow(() -> new SecurityException("delegated actor membership not found"));
        if (!membership.getTenant().isEnabled()
                || membership.getStatus() != MembershipStatus.ACTIVE) {
            throw new SecurityException("delegated actor membership is not active");
        }
        return versions.current(tenantId, actorSubjectId);
    }
}
