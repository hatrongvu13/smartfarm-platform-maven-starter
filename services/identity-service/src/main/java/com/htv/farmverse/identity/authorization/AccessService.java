package com.htv.farmverse.identity.authorization;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.htv.farmverse.identity.tenant.Membership;
import com.htv.farmverse.identity.tenant.MembershipRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tính quyền hiệu lực của một membership để nhúng vào access token (phân quyền kiểu "quyền nằm trong JWT").
 * Resource server không gọi lại identity: mọi thứ cần để quyết định đã nằm trong token.
 */
@Service
@RequiredArgsConstructor
public class AccessService {

    private final MembershipRepository memberships;

    @Transactional(readOnly = true)
    public TenantAccess accessOf(String membershipId) {
        Membership membership = memberships.findWithAuthorizationById(membershipId)
                .filter(Membership::isActive)
                .orElseThrow(() -> new IllegalStateException("Membership is not active: " + membershipId));

        Set<String> roles = new LinkedHashSet<>();
        Set<String> permissions = new LinkedHashSet<>();
        membership.getRoles().stream().sorted(Comparator.comparing(Role::getCode)).forEach(role -> {
            roles.add(role.getCode());
            role.getPermissions().stream().map(Permission::getCode).sorted().forEach(permissions::add);
        });

        return new TenantAccess(
                membership.getUser().getId(),
                membership.getUser().getEmail(),
                membership.getUser().getDisplayName(),
                membership.getTenant().getId(),
                membership.getTenant().getCode(),
                membership.getId(),
                List.copyOf(roles),
                List.copyOf(permissions));
    }

    /** Ảnh chụp quyền của một người trong một tenant tại lúc cấp token. */
    public record TenantAccess(
            String userId,
            String email,
            String displayName,
            String tenantId,
            String tenantCode,
            String membershipId,
            List<String> roles,
            List<String> permissions
    ) {
    }
}
