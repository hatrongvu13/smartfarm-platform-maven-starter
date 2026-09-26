package com.htv.smartfarm.identity.user;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserJpaRepository extends JpaRepository<UserEntity, String> {

    Optional<UserEntity> findByTenantIdAndEmail(String tenantId, String email);

    @Query("select distinct rp.permissionCode from UserRoleEntity ur, RolePermissionEntity rp "
            + "where rp.roleCode = ur.roleCode and ur.userId = :userId order by rp.permissionCode")
    List<String> findScopes(@Param("userId") String userId);

    @Query("select ur.roleCode from UserRoleEntity ur where ur.userId = :userId order by ur.roleCode")
    List<String> findRoleCodes(@Param("userId") String userId);
}
