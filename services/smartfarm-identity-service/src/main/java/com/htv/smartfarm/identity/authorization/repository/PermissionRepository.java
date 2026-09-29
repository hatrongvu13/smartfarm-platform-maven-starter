package com.htv.smartfarm.identity.authorization.repository;

import java.util.List;
import java.util.Optional;

import com.htv.smartfarm.identity.authorization.domain.PermissionEntity;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionRepository
        extends JpaRepository<PermissionEntity, String> {

    Optional<PermissionEntity> findByResourceTypeAndAction(
            String resourceType,
            String action
    );

    List<PermissionEntity> findAllByResourceTypeOrderByActionAsc(
            String resourceType
    );

    boolean existsByResourceTypeAndAction(
            String resourceType,
            String action
    );
}