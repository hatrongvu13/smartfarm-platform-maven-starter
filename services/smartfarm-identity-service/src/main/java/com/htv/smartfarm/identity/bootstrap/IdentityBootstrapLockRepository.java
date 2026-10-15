package com.htv.smartfarm.identity.bootstrap;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface IdentityBootstrapLockRepository
        extends JpaRepository<IdentityBootstrapLockEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select bootstrapLock
            from IdentityBootstrapLockEntity bootstrapLock
            where bootstrapLock.id = :id
            """)
    Optional<IdentityBootstrapLockEntity> findByIdForUpdate(
            @Param("id") String id
    );
}
