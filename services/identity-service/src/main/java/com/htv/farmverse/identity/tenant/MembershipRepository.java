package com.htv.farmverse.identity.tenant;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MembershipRepository extends JpaRepository<Membership, String> {

    @EntityGraph(attributePaths = {"tenant", "user", "roles", "roles.permissions"})
    Optional<Membership> findWithAuthorizationById(String id);

    @EntityGraph(attributePaths = {"tenant"})
    List<Membership> findByUserIdAndStatus(String userId, MembershipStatus status);

    Optional<Membership> findByTenantIdAndUserId(String tenantId, String userId);

    @EntityGraph(attributePaths = {"user", "roles"})
    Page<Membership> findByTenantId(String tenantId, Pageable pageable);

}
