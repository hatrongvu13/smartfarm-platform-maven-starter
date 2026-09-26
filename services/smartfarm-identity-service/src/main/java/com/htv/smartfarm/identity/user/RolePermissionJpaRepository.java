package com.htv.smartfarm.identity.user;

import org.springframework.data.jpa.repository.JpaRepository;


/** Spring Data JPA access to {@code sf_role_permission}. */
public interface RolePermissionJpaRepository
        extends JpaRepository<RolePermissionEntity, RolePermissionEntity.Key> {
}
