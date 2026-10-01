package com.htv.smartfarm.identity.mfa.repository;

import java.util.List;
import java.util.Optional;

import com.htv.smartfarm.identity.mfa.domain.AuthenticatorStatus;
import com.htv.smartfarm.identity.mfa.domain.AuthenticatorType;
import com.htv.smartfarm.identity.mfa.domain.UserAuthenticatorEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface UserAuthenticatorRepository
        extends JpaRepository<UserAuthenticatorEntity, String> {

    List<UserAuthenticatorEntity> findAllByUserIdOrderByCreatedAtAsc(
            String userId
    );

    Optional<UserAuthenticatorEntity> findFirstByUserIdAndTypeAndStatus(
            String userId,
            AuthenticatorType type,
            AuthenticatorStatus status
    );

    boolean existsByUserIdAndTypeAndStatus(
            String userId,
            AuthenticatorType type,
            AuthenticatorStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select a
            from UserAuthenticatorEntity a
            where a.userId = :userId
              and a.type = :type
              and a.status = :status
            order by a.createdAt
            """)
    List<UserAuthenticatorEntity> findActiveForUpdate(
            @Param("userId") String userId,
            @Param("type") AuthenticatorType type,
            @Param("status") AuthenticatorStatus status
    );
}
