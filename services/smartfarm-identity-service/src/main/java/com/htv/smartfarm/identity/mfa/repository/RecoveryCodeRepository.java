package com.htv.smartfarm.identity.mfa.repository;

import java.util.List;
import java.util.Optional;

import com.htv.smartfarm.identity.mfa.domain.RecoveryCodeEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface RecoveryCodeRepository
        extends JpaRepository<RecoveryCodeEntity, String> {

    List<RecoveryCodeEntity> findAllByAuthenticatorIdOrderByCreatedAtAsc(
            String authenticatorId
    );

    Optional<RecoveryCodeEntity> findByAuthenticatorIdAndCodeHashAndUsedAtIsNull(
            String authenticatorId,
            String codeHash
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select r
            from RecoveryCodeEntity r
            where r.authenticatorId = :authenticatorId
              and r.codeHash = :codeHash
              and r.usedAt is null
            """)
    Optional<RecoveryCodeEntity> findUnusedForUpdate(
            @Param("authenticatorId") String authenticatorId,
            @Param("codeHash") String codeHash
    );

    boolean existsByAuthenticatorIdAndUsedAtIsNull(String authenticatorId);

    long deleteByAuthenticatorId(String authenticatorId);
}
