package com.htv.farmverse.identity.authorization;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, String> {

    @EntityGraph(attributePaths = "permissions")
    Optional<Role> findByTenantIdAndCode(String tenantId, String code);

    @EntityGraph(attributePaths = "permissions")
    List<Role> findByTenantIdOrderByCode(String tenantId);

    List<Role> findByTenantIdAndCodeIn(String tenantId, java.util.Collection<String> codes);

    boolean existsByPermissionsCode(String permissionCode);
}
