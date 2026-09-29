package com.htv.smartfarm.identity.oauth2;

import java.time.Clock;
import java.util.Locale;

import com.htv.smartfarm.identity.account.domain.UserAccountEntity;
import com.htv.smartfarm.identity.account.repository.UserAccountRepository;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SmartFarmUserDetailsService
        implements UserDetailsService {

    private final UserAccountRepository userAccountRepository;
    private final Clock clock;

    public SmartFarmUserDetailsService(
            UserAccountRepository userAccountRepository,
            Clock clock
    ) {
        this.userAccountRepository = userAccountRepository;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        String normalizedEmail = email
                .trim()
                .toLowerCase(Locale.ROOT);

        UserAccountEntity account = userAccountRepository
                .findByNormalizedEmail(normalizedEmail)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Account not found"
                ));

        boolean accountNonLocked =
                account.isLoginAllowed(clock.instant());

        return User.withUsername(account.getId())
                .password(account.getPasswordHash())
                .disabled(!account.isEnabled())
                .accountLocked(!accountNonLocked)
                .authorities("IDENTITY_USER")
                .build();
    }
}