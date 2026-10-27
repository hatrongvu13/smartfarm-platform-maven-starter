package com.htv.farmverse.identity.tenant;

import java.time.Clock;
import java.util.Set;

import com.htv.farmverse.identity.account.AccountService;
import com.htv.farmverse.identity.account.UserAccount;
import com.htv.farmverse.identity.account.UserAccountRepository;
import com.htv.farmverse.identity.authorization.RoleAdministrationService;
import com.htv.farmverse.identity.shared.IdentityErrors;
import com.htv.farmverse.service.web.ApiProblem;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Quản lý người dùng trong một tenant: thêm, đổi trạng thái, gán role. */
@Service
@RequiredArgsConstructor
public class UserAdministrationService {

    private final MembershipRepository memberships;
    private final TenantRepository tenants;
    private final UserAccountRepository accounts;
    private final AccountService accountService;
    private final RoleAdministrationService roles;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Page<UserView> list(String tenantId, Pageable pageable) {
        return memberships.findByTenantId(tenantId, pageable).map(this::view);
    }

    @Transactional(readOnly = true)
    public UserView get(String tenantId, String userId) {
        return view(membership(tenantId, userId));
    }

    /**
     * Thêm người vào tenant. Email đã có tài khoản thì chỉ tạo membership (mật khẩu bỏ qua);
     * chưa có thì tạo tài khoản mới với mật khẩu đã cho.
     */
    @Transactional
    public UserView add(String tenantId, String email, String displayName, String password, Set<String> roleCodes) {
        UserAccount account = accounts.findByNormalizedEmail(UserAccount.normalize(email))
                .orElseGet(() -> {
                    if (password == null || password.isBlank()) {
                        throw ApiProblem.badRequest(IdentityErrors.WRONG_PASSWORD, "Tài khoản mới cần mật khẩu");
                    }
                    return accountService.create(email, password, displayName);
                });
        if (memberships.findByTenantIdAndUserId(tenantId, account.getId()).isPresent()) {
            throw ApiProblem.conflict(IdentityErrors.ALREADY_MEMBER, "Người dùng đã thuộc tenant");
        }
        Membership membership = new Membership(tenants.getReferenceById(tenantId), account);
        membership.replaceRoles(roles.resolveRoles(tenantId, roleCodes));
        return view(memberships.save(membership));
    }

    @Transactional
    public UserView changeStatus(String tenantId, String actorUserId, String userId, MembershipStatus status) {
        if (actorUserId.equals(userId) && status != MembershipStatus.ACTIVE) {
            throw ApiProblem.badRequest(IdentityErrors.SELF_LOCKOUT, "Không tự khóa chính mình");
        }
        Membership membership = membership(tenantId, userId);
        membership.changeStatus(status);
        return view(membership);
    }

    @Transactional
    public UserView replaceRoles(String tenantId, String userId, Set<String> roleCodes) {
        Membership membership = membership(tenantId, userId);
        membership.replaceRoles(roles.resolveRoles(tenantId, roleCodes));
        return view(membership);
    }

    @Transactional
    public void unlock(String tenantId, String userId) {
        membership(tenantId, userId); // chỉ mở khóa người thuộc tenant mình
        accountService.unlock(userId);
    }

    private Membership membership(String tenantId, String userId) {
        return memberships.findByTenantIdAndUserId(tenantId, userId)
                .orElseThrow(() -> ApiProblem.notFound(IdentityErrors.MEMBERSHIP_NOT_FOUND, "Người dùng không thuộc tenant"));
    }

    private UserView view(Membership membership) {
        return UserView.of(membership, clock.instant());
    }
}
