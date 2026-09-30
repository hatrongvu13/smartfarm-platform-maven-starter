package com.htv.smartfarm.identity.token;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshJpaRepository
        extends JpaRepository<RefreshEntity, String> {

    Optional<RefreshEntity> findByTokenHash(String tokenHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RefreshEntity r set r.revoked = true where r.id = :id and r.revoked = false")
    int consumeIfActive(@Param("id") String id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RefreshEntity r set r.revoked = true where r.familyId = :family")
    int revokeFamily(@Param("family") String family);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RefreshEntity r set r.revoked = true where r.userId = :userId")
    int revokeUser(@Param("userId") String userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RefreshEntity r set r.revoked = true where r.userId = :userId and r.tenantId = :tenantId")
    int revokeUserInTenant(
            @Param("userId") String userId,
            @Param("tenantId") String tenantId
    );
}
