package com.htv.smartfarm.identity.authorization.repository;

import java.util.List;
import java.util.Optional;

import com.htv.smartfarm.identity.authorization.domain.RoleEntity;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository
        extends JpaRepository<RoleEntity, String> {

    @EntityGraph(attributePaths = "tenant")
    Optional<RoleEntity> findByTenantIdAndCode(
            String tenantId,
            String code
    );

    @EntityGraph(attributePaths = "tenant")
    List<RoleEntity> findAllByTenantIdOrderByCodeAsc(
            String tenantId
    );

    boolean existsByTenantIdAndCode(
            String tenantId,
            String code
    );
}