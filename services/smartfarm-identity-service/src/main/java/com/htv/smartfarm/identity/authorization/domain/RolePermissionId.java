package com.htv.smartfarm.identity.authorization.domain;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class RolePermissionId implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Column(name = "role_id", nullable = false, length = 36)
    private String roleId;

    @Column(name = "permission_code", nullable = false, length = 80)
    private String permissionCode;

    protected RolePermissionId() {
    }

    public RolePermissionId(String roleId, String permissionCode) {
        this.roleId = roleId;
        this.permissionCode = permissionCode;
    }

    public String getRoleId() {
        return roleId;
    }

    public String getPermissionCode() {
        return permissionCode;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RolePermissionId that)) return false;
        return Objects.equals(roleId, that.roleId)
                && Objects.equals(permissionCode, that.permissionCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(roleId, permissionCode);
    }
}
