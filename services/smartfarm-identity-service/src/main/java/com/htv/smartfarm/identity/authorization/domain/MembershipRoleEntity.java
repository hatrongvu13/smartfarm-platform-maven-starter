package com.htv.smartfarm.identity.authorization.domain;

import java.time.Instant;

import com.htv.smartfarm.identity.tenant.domain.TenantMembershipEntity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

@Entity
@Table(name = "sf_membership_role")
public class MembershipRoleEntity {

    @EmbeddedId
    private MembershipRoleId id;

    @MapsId("membershipId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "membership_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_membership_role_membership")
    )
    private TenantMembershipEntity membership;

    @MapsId("roleId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "role_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_membership_role_role")
    )
    private RoleEntity role;

    @Column(name = "granted_at", nullable = false, updatable = false)
    private Instant grantedAt;

    @Column(name = "granted_by", nullable = false, updatable = false, length = 36)
    private String grantedBy;

    protected MembershipRoleEntity() {
    }

    public MembershipRoleEntity(
            TenantMembershipEntity membership,
            RoleEntity role,
            String grantedBy
    ) {
        if (!membership.getTenant().getId().equals(role.getTenant().getId())) {
            throw new IllegalArgumentException(
                    "Membership and role must belong to the same tenant"
            );
        }

        this.membership = membership;
        this.role = role;
        this.grantedBy = grantedBy;
        this.grantedAt = Instant.now();
        this.id = new MembershipRoleId(
                membership.getId(),
                role.getId()
        );
    }

    public TenantMembershipEntity getMembership() {
        return membership;
    }

    public RoleEntity getRole() {
        return role;
    }

    public Instant getGrantedAt() {
        return grantedAt;
    }

    public String getGrantedBy() {
        return grantedBy;
    }
}