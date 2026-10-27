package com.htv.farmverse.identity.tenant;

import java.util.LinkedHashSet;
import java.util.Set;

import com.htv.farmverse.identity.account.UserAccount;
import com.htv.farmverse.identity.authorization.Role;
import com.htv.farmverse.identity.shared.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Việc một account thuộc một tenant. Quyền gắn vào membership, không gắn vào account:
 * cùng một người có thể là ADMIN ở tenant này và USER ở tenant khác.
 */
@Getter
@Entity
@Table(name = "membership")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Membership extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private UserAccount user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MembershipStatus status = MembershipStatus.ACTIVE;

    @ManyToMany
    @JoinTable(name = "membership_role",
            joinColumns = @JoinColumn(name = "membership_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new LinkedHashSet<>();

    public Membership(Tenant tenant, UserAccount user) {
        this.tenant = tenant;
        this.user = user;
    }

    public boolean isActive() {
        return status == MembershipStatus.ACTIVE;
    }

    public void changeStatus(MembershipStatus status) {
        this.status = status;
    }

    public void replaceRoles(Set<Role> roles) {
        this.roles.clear();
        this.roles.addAll(roles);
    }

    public void addRole(Role role) {
        this.roles.add(role);
    }
}
