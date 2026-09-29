package com.htv.smartfarm.identity.tenant.domain;

import com.htv.smartfarm.identity.account.domain.UserAccountEntity;
import com.htv.smartfarm.identity.shared.persistence.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
        name = "sf_tenant_membership",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_membership_tenant_user",
                        columnNames = {"tenant_id", "user_id"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_membership_user",
                        columnList = "user_id"
                ),
                @Index(
                        name = "idx_membership_tenant_status",
                        columnList = "tenant_id,status"
                )
        }
)
public class TenantMembershipEntity extends AuditableEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "tenant_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_membership_tenant")
    )
    private TenantEntity tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_membership_user")
    )
    private UserAccountEntity user;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MembershipStatus status;

    protected TenantMembershipEntity() {
    }

    public TenantMembershipEntity(
            String id,
            TenantEntity tenant,
            UserAccountEntity user
    ) {
        this.id = id;
        this.tenant = tenant;
        this.user = user;
        this.status = MembershipStatus.INVITED;
    }

    public void activate() {
        this.status = MembershipStatus.ACTIVE;
    }

    public void suspend() {
        this.status = MembershipStatus.SUSPENDED;
    }

    public void disable() {
        this.status = MembershipStatus.DISABLED;
    }

    public boolean isActive() {
        return status == MembershipStatus.ACTIVE;
    }

    public String getId() {
        return id;
    }

    public TenantEntity getTenant() {
        return tenant;
    }

    public UserAccountEntity getUser() {
        return user;
    }

    public MembershipStatus getStatus() {
        return status;
    }
}