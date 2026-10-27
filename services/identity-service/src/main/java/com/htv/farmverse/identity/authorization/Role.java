package com.htv.farmverse.identity.authorization;

import java.util.LinkedHashSet;
import java.util.Set;

import com.htv.farmverse.identity.shared.BaseEntity;
import com.htv.farmverse.identity.tenant.Tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Nhóm permission có tên, thuộc một tenant. */
@Getter
@Entity
@Table(name = "role")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Role extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 255)
    private String description;

    /** Role nền tảng (ví dụ SUPER_ADMIN): không cho sửa / xóa qua API. */
    @Column(name = "system_owned", nullable = false)
    private boolean systemOwned;

    @ManyToMany
    @JoinTable(name = "role_permission",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_code"))
    private Set<Permission> permissions = new LinkedHashSet<>();

    public Role(Tenant tenant, String code, String name, String description, boolean systemOwned) {
        this.tenant = tenant;
        this.code = code;
        this.name = name;
        this.description = description;
        this.systemOwned = systemOwned;
    }

    public void rename(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public void replacePermissions(Set<Permission> permissions) {
        this.permissions.clear();
        this.permissions.addAll(permissions);
    }
}
