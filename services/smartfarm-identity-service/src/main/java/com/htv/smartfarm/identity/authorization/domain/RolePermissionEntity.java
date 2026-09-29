package com.htv.smartfarm.identity.authorization.domain;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

@Entity
@Table(name = "sf_role_permission")
public class RolePermissionEntity {

    @EmbeddedId
    private RolePermissionId id;

    @MapsId("roleId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "role_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_role_permission_role")
    )
    private RoleEntity role;

    @MapsId("permissionCode")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "permission_code",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_role_permission_permission")
    )
    private PermissionEntity permission;

    protected RolePermissionEntity() {
    }

    public RolePermissionEntity(
            RoleEntity role,
            PermissionEntity permission
    ) {
        this.role = role;
        this.permission = permission;
        this.id = new RolePermissionId(
                role.getId(),
                permission.getCode()
        );
    }

    public RoleEntity getRole() {
        return role;
    }

    public PermissionEntity getPermission() {
        return permission;
    }
}