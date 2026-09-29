package com.htv.smartfarm.identity.authorization.domain;

import com.htv.smartfarm.identity.shared.persistence.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "sf_permission",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_permission_resource_action",
                        columnNames = {"resource_type", "action"}
                )
        }
)
public class PermissionEntity extends AuditableEntity {

    @Id
    @Column(name = "code", nullable = false, updatable = false, length = 80)
    private String code;

    @Column(name = "resource_type", nullable = false, length = 80)
    private String resourceType;

    @Column(name = "action", nullable = false, length = 40)
    private String action;

    @Column(name = "description", length = 255)
    private String description;

    protected PermissionEntity() {
    }

    public PermissionEntity(
            String code,
            String resourceType,
            String action,
            String description
    ) {
        this.code = code;
        this.resourceType = resourceType;
        this.action = action;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getAction() {
        return action;
    }

    public String getDescription() {
        return description;
    }
}