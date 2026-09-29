package com.htv.smartfarm.identity.account.repository;

import java.util.Optional;

import com.htv.smartfarm.identity.account.domain.UserAccountEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface UserAccountRepository
        extends JpaRepository<UserAccountEntity, String> {

    Optional<UserAccountEntity> findByNormalizedEmail(
            String normalizedEmail
    );

    boolean existsByNormalizedEmail(String normalizedEmail);

    boolean existsByNormalizedEmailAndIdNot(
            String normalizedEmail,
            String excludedUserId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select u
            from UserAccountEntity u
            where u.id = :userId
            """)
    Optional<UserAccountEntity> findByIdForUpdate(
            @Param("userId") String userId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select u
            from UserAccountEntity u
            where u.normalizedEmail = :normalizedEmail
            """)
    Optional<UserAccountEntity> findByNormalizedEmailForUpdate(
            @Param("normalizedEmail") String normalizedEmail
    );
}