package com.htv.farmverse.identity.tenant;

import java.time.Instant;
import java.util.List;

import com.htv.farmverse.identity.authorization.Role;

/** Một người dùng nhìn từ góc một tenant. */
public record UserView(
        String userId,
        String email,
        String displayName,
        MembershipStatus status,
        List<String> roles,
        boolean locked,
        Instant lastLoginAt
) {

    static UserView of(Membership membership, Instant now) {
        var user = membership.getUser();
        return new UserView(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                membership.getStatus(),
                membership.getRoles().stream().map(Role::getCode).sorted().toList(),
                user.isLocked(now),
                user.getLastLoginAt());
    }
}
