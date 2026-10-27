package com.htv.farmverse.identity.login;

import java.time.Clock;

import com.htv.farmverse.identity.account.UserAccount;
import com.htv.farmverse.identity.account.UserAccountRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nạp tài khoản theo email cho form login. {@code username} của {@link UserDetails} trả về là
 * <b>id tài khoản</b> (không phải email) để bước chọn tenant phía sau dùng thẳng.
 */
@Service
@RequiredArgsConstructor
public class AccountUserDetailsService implements UserDetailsService {

    private final UserAccountRepository accounts;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        UserAccount account = accounts.findByNormalizedEmail(UserAccount.normalize(email))
                .orElseThrow(() -> new UsernameNotFoundException("Unknown account"));
        return User.withUsername(account.getId())
                .password(account.getPasswordHash())
                .disabled(!account.isEnabled())
                .accountLocked(account.isLocked(clock.instant()))
                .build();
    }
}
