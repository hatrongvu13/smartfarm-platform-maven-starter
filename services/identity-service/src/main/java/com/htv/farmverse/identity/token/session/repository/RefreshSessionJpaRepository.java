package com.htv.farmverse.identity.token.session.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import com.htv.farmverse.identity.token.session.entity.RefreshSessionEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshSessionJpaRepository extends JpaRepository<RefreshSessionEntity,String> {
    List<RefreshSessionEntity> findAllByUserIdAndTenantIdOrderByCreatedAtDesc(String userId,String tenantId);
    List<RefreshSessionEntity> findAllByUserIdOrderByCreatedAtDesc(String userId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from RefreshSessionEntity s where s.id=:id and s.userId=:userId and s.tenantId=:tenantId")
    Optional<RefreshSessionEntity> lockOwned(@Param("id") String id,@Param("userId") String userId,@Param("tenantId") String tenantId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from RefreshSessionEntity s where s.familyId=:familyId")
    Optional<RefreshSessionEntity> lockByFamilyId(@Param("familyId") String familyId);
    @Modifying
    @Query("delete from RefreshSessionEntity s where s.expiresAt<:before")
    int deleteExpired(@Param("before") Instant before);
}
