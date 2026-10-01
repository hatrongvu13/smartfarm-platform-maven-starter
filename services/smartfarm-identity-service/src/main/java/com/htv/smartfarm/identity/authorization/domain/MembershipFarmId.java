package com.htv.smartfarm.identity.authorization.domain;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class MembershipFarmId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(
            name = "membership_id",
            nullable = false,
            length = 36
    )
    private String membershipId;

    @Column(
            name = "farm_id",
            nullable = false,
            length = 36
    )
    private String farmId;

    protected MembershipFarmId() {
    }

    public MembershipFarmId(
            String membershipId,
            String farmId
    ) {
        this.membershipId = membershipId;
        this.farmId = farmId;
    }

    public String getMembershipId() {
        return membershipId;
    }

    public String getFarmId() {
        return farmId;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof MembershipFarmId other)) {
            return false;
        }

        return Objects.equals(
                membershipId,
                other.membershipId
        ) && Objects.equals(
                farmId,
                other.farmId
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                membershipId,
                farmId
        );
    }
}