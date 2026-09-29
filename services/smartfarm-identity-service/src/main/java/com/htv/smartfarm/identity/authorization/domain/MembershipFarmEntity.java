package com.htv.smartfarm.identity.authorization.domain;

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
@Table(name = "sf_membership_farm")
public class MembershipFarmEntity {

    @EmbeddedId
    private MembershipFarmId id;

    @MapsId("membershipId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "membership_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_membership_farm_membership")
    )
    private TenantMembershipEntity membership;

    @Column(
            name = "farm_id",
            nullable = false,
            insertable = false,
            updatable = false,
            length = 36
    )
    private String farmId;

    protected MembershipFarmEntity() {
    }

    public MembershipFarmEntity(
            TenantMembershipEntity membership,
            String farmId
    ) {
        this.membership = membership;
        this.farmId = farmId;
        this.id = new MembershipFarmId(
                membership.getId(),
                farmId
        );
    }

    public TenantMembershipEntity getMembership() {
        return membership;
    }

    public String getFarmId() {
        return farmId;
    }
}