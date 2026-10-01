package com.htv.smartfarm.identity.mfa.repository;

import java.time.Instant;
import java.util.Optional;

import com.htv.smartfarm.identity.mfa.domain.MfaChallengeEntity;
import com.htv.smartfarm.identity.mfa.domain.MfaChallengeStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface MfaChallengeRepository
        extends JpaRepository<MfaChallengeEntity, String> {

    Optional<MfaChallengeEntity> findByChallengeHash(String challengeHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select c
            from MfaChallengeEntity c
            where c.challengeHash = :challengeHash
            """)
    Optional<MfaChallengeEntity> findByChallengeHashForUpdate(
            @Param("challengeHash") String challengeHash
    );

    long deleteByExpiresAtBeforeAndStatus(
            Instant expiresAt,
            MfaChallengeStatus status
    );
}
