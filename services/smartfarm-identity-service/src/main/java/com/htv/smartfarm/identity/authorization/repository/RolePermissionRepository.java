package com.htv.smartfarm.identity.authorization.repository;

import java.util.Collection;
import java.util.List;

import com.htv.smartfarm.identity.authorization.domain.RolePermissionEntity;
import com.htv.smartfarm.identity.authorization.domain.RolePermissionId;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RolePermissionRepository
        extends JpaRepository<RolePermissionEntity, RolePermissionId> {

    @EntityGraph(attributePaths = {"role", "permission"})
    List<RolePermissionEntity> findAllByIdRoleId(String roleId);

    boolean existsByIdRoleIdAndIdPermissionCode(
            String roleId,
            String permissionCode
    );

    @Query("""
            select count(rp)
            from RolePermissionEntity rp
            where rp.id.roleId in :roleIds
              and rp.permission.resourceType = :resourceType
              and rp.permission.action = :action
            """)
    long countGrantedPermission(
            @Param("roleIds") Collection<String> roleIds,
            @Param("resourceType") String resourceType,
            @Param("action") String action
    );

    @Query("""
            select distinct rp.id.permissionCode
            from RolePermissionEntity rp
            where rp.id.roleId in :roleIds
            order by rp.id.permissionCode
            """)
    List<String> findPermissionCodesByRoleIds(
            @Param("roleIds") Collection<String> roleIds
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from RolePermissionEntity rp
            where rp.id.roleId = :roleId
            """)
    int deleteAllByRoleId(@Param("roleId") String roleId);
}
