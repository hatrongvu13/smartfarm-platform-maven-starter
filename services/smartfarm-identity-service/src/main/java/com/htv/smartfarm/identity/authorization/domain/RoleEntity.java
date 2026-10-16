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
import lombok.Getter;

@Getter
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

    @Column(name = "description", length = 255)
    private String description;

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
        this(id, tenant, code, name, null, systemRole);
    }

    public RoleEntity(
            String id,
            TenantEntity tenant,
            String code,
            String name,
            String description,
            boolean systemRole
    ) {
        this.id = id;
        this.tenant = tenant;
        this.code = code;
        this.name = name;
        this.description = normalizeNullable(description);
        this.systemRole = systemRole;
    }

    public void rename(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank");
        this.name = name.trim();
    }

    public void updateDescription(String description) {
        this.description = normalizeNullable(description);
    }

    private static String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > 255) {
            throw new IllegalArgumentException(
                    "description must not exceed 255 characters"
            );
        }
        return normalized;
    }

}