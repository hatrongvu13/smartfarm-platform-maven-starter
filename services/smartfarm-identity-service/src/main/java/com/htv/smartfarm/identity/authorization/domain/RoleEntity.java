package com.htv.smartfarm.identity.authorization.domain;

import com.htv.smartfarm.identity.shared.persistence.AuditableEntity;
import com.htv.smartfarm.identity.tenant.domain.TenantEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "sf_role",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_role_tenant_code",
                        columnNames = {"tenant_id", "code"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_role_tenant",
                        columnList = "tenant_id"
                )
        }
)
public class RoleEntity extends AuditableEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "tenant_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_role_tenant")
    )
    private TenantEntity tenant;

    @Column(name = "code", nullable = false, length = 40)
    private String code;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "system_role", nullable = false)
    private boolean systemRole;

    protected RoleEntity() {
    }

    public RoleEntity(
            String id,
            TenantEntity tenant,
            String code,
            String name,
            boolean systemRole
    ) {
        this.id = id;
        this.tenant = tenant;
        this.code = code;
        this.name = name;
        this.systemRole = systemRole;
    }

    public String getId() {
        return id;
    }

    public TenantEntity getTenant() {
        return tenant;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public boolean isSystemRole() {
        return systemRole;
    }
}