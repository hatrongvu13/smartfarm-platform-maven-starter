package com.htv.smartfarm.identity.authorization.domain;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Embeddable;

@Embeddable
public class MembershipRoleId implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String membershipId;
    private String roleId;

    protected MembershipRoleId() {
    }

    public MembershipRoleId(String membershipId, String roleId) {
        this.membershipId = membershipId;
        this.roleId = roleId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof MembershipRoleId that)) {
            return false;
        }

        return Objects.equals(membershipId, that.membershipId)
                && Objects.equals(roleId, that.roleId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(membershipId, roleId);
    }
}