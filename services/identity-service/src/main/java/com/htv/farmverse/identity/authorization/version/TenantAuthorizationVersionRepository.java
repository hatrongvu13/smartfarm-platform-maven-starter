package com.htv.farmverse.identity.authorization.version;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TenantAuthorizationVersionRepository
        extends JpaRepository<TenantAuthorizationVersionEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from TenantAuthorizationVersionEntity v where v.tenantId = :tenantId")
    Optional<TenantAuthorizationVersionEntity> findForUpdate(
            @Param("tenantId") String tenantId
    );
}
