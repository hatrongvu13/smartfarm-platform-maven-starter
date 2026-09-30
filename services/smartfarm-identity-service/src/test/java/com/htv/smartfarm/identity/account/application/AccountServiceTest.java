package com.htv.smartfarm.identity.account.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import com.htv.smartfarm.common.exception.ConflictException;
import com.htv.smartfarm.common.exception.NotFoundException;
import com.htv.smartfarm.common.exception.ValidationException;
import com.htv.smartfarm.identity.account.domain.UserAccountEntity;
import com.htv.smartfarm.identity.account.repository.UserAccountRepository;
import com.htv.smartfarm.identity.account.repository.UserProfileRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    private static final String USER_ID = "user-001";
    private static final Instant NOW = Instant.parse("2026-09-30T00:00:00Z");

    @Mock
    private UserAccountRepository accountRepository;

    @Mock
    private UserProfileRepository profileRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserAccountEntity account;

    private AccountService service;

    @BeforeEach
    void setUp() {
        service = new AccountService(
                accountRepository,
                profileRepository,
                passwordEncoder,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void changePasswordShouldRejectInvalidCurrentPassword() {
        when(accountRepository.findByIdForUpdate(USER_ID))
                .thenReturn(Optional.of(account));
        when(account.getPasswordHash()).thenReturn("old-hash");
        when(passwordEncoder.matches("wrong-password", "old-hash"))
                .thenReturn(false);

        assertThatThrownBy(() -> service.changePassword(
                USER_ID,
                "wrong-password",
                "a-new-password-123"
        ))
                .isInstanceOf(ValidationException.class)
                .satisfies(exception -> assertThat(
                        ((ValidationException) exception).code()
                ).isEqualTo("CURRENT_PASSWORD_INVALID"));
    }

    @Test
    void changePasswordShouldRejectPasswordReuse() {
        when(accountRepository.findByIdForUpdate(USER_ID))
                .thenReturn(Optional.of(account));
        when(account.getPasswordHash()).thenReturn("old-hash");
        when(passwordEncoder.matches("current-password", "old-hash"))
                .thenReturn(true);
        when(passwordEncoder.matches("same-password-123", "old-hash"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.changePassword(
                USER_ID,
                "current-password",
                "same-password-123"
        ))
                .isInstanceOf(ConflictException.class)
                .satisfies(exception -> assertThat(
                        ((ConflictException) exception).code()
                ).isEqualTo("PASSWORD_REUSE_NOT_ALLOWED"));
    }

    @Test
    void resetPasswordShouldEncodeAndStorePassword() {
        when(accountRepository.findByIdForUpdate(USER_ID))
                .thenReturn(Optional.of(account));
        when(passwordEncoder.encode("new-password-123"))
                .thenReturn("new-hash");

        service.resetPassword(USER_ID, "new-password-123");

        verify(account).changePasswordHash("new-hash");
    }

    @Test
    void isLoginAllowedShouldThrowNotFound() {
        when(accountRepository.findById(USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.isLoginAllowed(USER_ID))
                .isInstanceOf(NotFoundException.class)
                .satisfies(exception -> assertThat(
                        ((NotFoundException) exception).code()
                ).isEqualTo("USERACCOUNT_NOT_FOUND"));
    }

    @Test
    void recordLoginFailureShouldUseFixedClock() {
        when(accountRepository.findByNormalizedEmailForUpdate(
                "user@example.com"
        )).thenReturn(Optional.of(account));

        service.recordLoginFailure(" User@Example.com ");

        verify(account).recordLoginFailure(
                5,
                NOW.plusSeconds(15 * 60)
        );
    }
}
