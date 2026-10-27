package com.htv.farmverse.identity.login;

import java.util.List;

import com.htv.farmverse.identity.account.AccountService;
import com.htv.farmverse.identity.tenant.Membership;
import com.htv.farmverse.identity.tenant.MembershipRepository;
import com.htv.farmverse.identity.tenant.MembershipStatus;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Form login có chọn tenant, dựng trên {@link DaoAuthenticationProvider} chuẩn:
 * <ol>
 *   <li>Spring kiểm email + mật khẩu (BCrypt), tài khoản bị khóa / tắt.</li>
 *   <li>Lớp này chọn membership ACTIVE theo mã tenant (hoặc membership duy nhất nếu không nhập).</li>
 *   <li>Principal kết quả có {@code username = membershipId}: SAS lưu principal này vào
 *       {@code oauth2_authorization.principal_name}, và lúc cấp token sẽ biết đúng tenant.</li>
 * </ol>
 * Đếm lần sai mật khẩu để khóa tạm cũng làm ở đây.
 */
public class TenantAuthenticationProvider extends DaoAuthenticationProvider {

    private final MembershipRepository memberships;
    private final AccountService accounts;

    public TenantAuthenticationProvider(AccountUserDetailsService userDetailsService,
                                        PasswordEncoder passwordEncoder,
                                        MembershipRepository memberships,
                                        AccountService accounts) {
        super(userDetailsService);
        setPasswordEncoder(passwordEncoder);
        this.memberships = memberships;
        this.accounts = accounts;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        try {
            return super.authenticate(authentication);
        } catch (TenantSelectionRequiredException e) {
            throw e;
        } catch (BadCredentialsException e) {
            accounts.recordLoginFailure(String.valueOf(authentication.getPrincipal()));
            throw e;
        }
    }

    @Override
    protected Authentication createSuccessAuthentication(Object principal, Authentication authentication, UserDetails user) {
        String accountId = user.getUsername();
        String tenantCode = authentication.getDetails() instanceof TenantLoginDetails details ? details.tenantCode() : null;
        Membership membership = chooseMembership(accountId, tenantCode);
        accounts.recordLoginSuccess(accountId);

        UserDetails member = User.withUsername(membership.getId()).password("").authorities(List.of()).build();
        Authentication result = super.createSuccessAuthentication(member, authentication, member);
        if (result instanceof UsernamePasswordAuthenticationToken token
                && authentication.getDetails() instanceof TenantLoginDetails details) {
            token.setDetails(details.plain());
        }
        return result;
    }

    private Membership chooseMembership(String accountId, String tenantCode) {
        List<Membership> active = memberships.findByUserIdAndStatus(accountId, MembershipStatus.ACTIVE);
        if (tenantCode != null) {
            return active.stream()
                    .filter(m -> m.getTenant().getCode().equalsIgnoreCase(tenantCode) && m.getTenant().isEnabled())
                    .findFirst()
                    .orElseThrow(TenantSelectionRequiredException::new);
        }
        List<Membership> usable = active.stream().filter(m -> m.getTenant().isEnabled()).toList();
        if (usable.size() != 1) {
            throw new TenantSelectionRequiredException();
        }
        return usable.get(0);
    }
}
