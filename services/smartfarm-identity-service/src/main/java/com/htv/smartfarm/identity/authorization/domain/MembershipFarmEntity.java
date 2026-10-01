package com.htv.smartfarm.identity.authorization.domain;

import com.htv.smartfarm.identity.tenant.domain.TenantMembershipEntity;
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
    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "membership_id",
            nullable = false,
            foreignKey = @ForeignKey(
                    name = "fk_membership_farm_membership"
            )
    )
    private TenantMembershipEntity membership;

    protected MembershipFarmEntity() {
    }

    public MembershipFarmEntity(
            TenantMembershipEntity membership,
            String farmId
    ) {
        if (membership == null) {
            throw new IllegalArgumentException(
                    "membership must not be null"
            );
        }
        if (membership.getId() == null
                || membership.getId().isBlank()) {
            throw new IllegalArgumentException(
                    "membership ID must not be blank"
            );
        }
        if (farmId == null || farmId.isBlank()) {
            throw new IllegalArgumentException(
                    "farmId must not be blank"
            );
        }
        this.membership = membership;
        this.id = new MembershipFarmId(
                membership.getId(),
                farmId.trim()
        );
    }

    public MembershipFarmId getId() {
        return id;
    }

    public TenantMembershipEntity getMembership() {
        return membership;
    }

    public String getMembershipId() {
        return id == null
                ? null
                : id.getMembershipId();
    }

    public String getFarmId() {
        return id == null
                ? null
                : id.getFarmId();
    }
}