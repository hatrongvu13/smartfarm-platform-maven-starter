package com.htv.smartfarm.identity.authorization.domain;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class MembershipRoleId implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Column(name = "membership_id", nullable = false, length = 36)
    private String membershipId;

    @Column(name = "role_id", nullable = false, length = 36)
    private String roleId;

    protected MembershipRoleId() {
    }

    public MembershipRoleId(String membershipId, String roleId) {
        this.membershipId = membershipId;
        this.roleId = roleId;
    }

    public String getMembershipId() {
        return membershipId;
    }

    public String getRoleId() {
        return roleId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof MembershipRoleId that)) return false;
        return Objects.equals(membershipId, that.membershipId)
                && Objects.equals(roleId, that.roleId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(membershipId, roleId);
    }
}
