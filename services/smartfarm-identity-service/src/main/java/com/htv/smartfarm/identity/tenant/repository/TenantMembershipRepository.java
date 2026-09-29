package com.htv.smartfarm.identity.tenant.repository;

import java.util.List;
import java.util.Optional;

import com.htv.smartfarm.identity.tenant.domain.MembershipStatus;
import com.htv.smartfarm.identity.tenant.domain.TenantMembershipEntity;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface TenantMembershipRepository
        extends JpaRepository<TenantMembershipEntity, String> {

    @EntityGraph(attributePaths = {
            "tenant",
            "user"
    })
    Optional<TenantMembershipEntity> findByTenantIdAndUserId(
            String tenantId,
            String userId
    );

    @EntityGraph(attributePaths = {
            "tenant"
    })
    List<TenantMembershipEntity> findAllByUserIdOrderByTenantNameAsc(
            String userId
    );

    @EntityGraph(attributePaths = {
            "user"
    })
    List<TenantMembershipEntity> findAllByTenantIdAndStatus(
            String tenantId,
            MembershipStatus status
    );

    boolean existsByTenantIdAndUserId(
            String tenantId,
            String userId
    );

    boolean existsByTenantIdAndUserIdAndStatus(
            String tenantId,
            String userId,
            MembershipStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select m
        from TenantMembershipEntity m
        join fetch m.tenant
        join fetch m.user
        where m.tenant.id = :tenantId
          and m.user.id = :userId
        """)
    Optional<TenantMembershipEntity> findByTenantAndUserForUpdate(
            @Param("tenantId") String tenantId,
            @Param("userId") String userId
    );

    @Query("""
        select m.id
        from TenantMembershipEntity m
        where m.tenant.id = :tenantId
          and m.user.id = :userId
          and m.status = :status
          and m.tenant.enabled = true
          and m.user.enabled = true
        """)
    Optional<String> findActiveMembershipId(
            @Param("tenantId") String tenantId,
            @Param("userId") String userId,
            @Param("status") MembershipStatus status
    );
}