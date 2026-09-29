package com.htv.smartfarm.identity.authorization.domain;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Embeddable;

@Embeddable
public class MembershipFarmId implements Serializable {

    private static final long serialVersionUID = 1L;

    private String membershipId;
    private String farmId;

    protected MembershipFarmId() {
    }

    public MembershipFarmId(String membershipId, String farmId) {
        this.membershipId = membershipId;
        this.farmId = farmId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof MembershipFarmId that)) {
            return false;
        }

        return Objects.equals(membershipId, that.membershipId)
                && Objects.equals(farmId, that.farmId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(membershipId, farmId);
    }
}