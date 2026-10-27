package com.htv.farmverse.identity.tenant.domain;

import com.htv.farmverse.identity.shared.persistence.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

@Getter
@Entity
@Table(
        name = "sf_tenant",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_tenant_code",
                        columnNames = "code"
                )
        }
)
public class TenantEntity extends AuditableEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private String id;

    @Column(name = "code", nullable = false, length = 100)
    private String code;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    protected TenantEntity() {
    }

    public TenantEntity(String id, String code, String name) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.enabled = true;
    }

}