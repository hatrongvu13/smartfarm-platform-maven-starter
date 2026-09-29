package com.htv.smartfarm.identity.account.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import com.htv.smartfarm.identity.account.application.command.CreateAccountCommand;
import com.htv.smartfarm.identity.account.domain.UserAccountEntity;
import com.htv.smartfarm.identity.account.domain.UserProfileEntity;
import com.htv.smartfarm.identity.account.repository.UserAccountRepository;
import com.htv.smartfarm.identity.account.repository.UserProfileRepository;
import com.htv.smartfarm.identity.shared.exception.ConflictException;
import com.htv.smartfarm.identity.shared.exception.EntityNotFoundException;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private static final int DEFAULT_LOCK_THRESHOLD = 5;
    private static final Duration DEFAULT_LOCK_DURATION =
            Duration.ofMinutes(15);

    private final UserAccountRepository userAccountRepository;
    private final UserProfileRepository userProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public AccountService(
            UserAccountRepository userAccountRepository,
            UserProfileRepository userProfileRepository,
            PasswordEncoder passwordEncoder,
            Clock clock
    ) {
        this.userAccountRepository = userAccountRepository;
        this.userProfileRepository = userProfileRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional
    public String createAccount(CreateAccountCommand command) {
        validateCreateCommand(command);

        String normalizedEmail = normalizeEmail(command.email());

        if (userAccountRepository.existsByNormalizedEmail(normalizedEmail)) {
            throw new ConflictException(
                    "Email is already assigned to another account"
            );
        }

        String userId = UUID.randomUUID().toString();

        UserAccountEntity account = new UserAccountEntity(
                userId,
                command.email().trim(),
                passwordEncoder.encode(command.rawPassword())
        );

        UserProfileEntity profile = new UserProfileEntity(
                account,
                normalizeDisplayName(command.displayName()),
                defaultIfBlank(command.locale(), "vi-VN"),
                defaultIfBlank(command.timeZone(), "Asia/Ho_Chi_Minh")
        );

        profile.update(
                normalizeDisplayName(command.displayName()),
                normalizeNullable(command.phoneNumber()),
                defaultIfBlank(command.locale(), "vi-VN"),
                defaultIfBlank(command.timeZone(), "Asia/Ho_Chi_Minh")
        );

        userAccountRepository.save(account);
        userProfileRepository.save(profile);

        return userId;
    }

    @Transactional
    public void changePassword(
            String userId,
            String currentRawPassword,
            String newRawPassword
    ) {
        requireText(currentRawPassword, "currentRawPassword");
        validatePassword(newRawPassword);

        UserAccountEntity account = userAccountRepository
                .findByIdForUpdate(userId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "UserAccount",
                        userId
                ));

        if (!passwordEncoder.matches(
                currentRawPassword,
                account.getPasswordHash()
        )) {
            throw new IllegalArgumentException(
                    "Current password is invalid"
            );
        }

        if (passwordEncoder.matches(
                newRawPassword,
                account.getPasswordHash()
        )) {
            throw new ConflictException(
                    "New password must differ from current password"
            );
        }

        account.changePasswordHash(
                passwordEncoder.encode(newRawPassword)
        );
    }

    @Transactional
    public void resetPassword(
            String userId,
            String newRawPassword
    ) {
        validatePassword(newRawPassword);

        UserAccountEntity account = userAccountRepository
                .findByIdForUpdate(userId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "UserAccount",
                        userId
                ));

        account.changePasswordHash(
                passwordEncoder.encode(newRawPassword)
        );
    }

    @Transactional
    public void recordLoginFailure(String normalizedEmail) {
        userAccountRepository
                .findByNormalizedEmailForUpdate(
                        normalizeEmail(normalizedEmail)
                )
                .ifPresent(account -> {
                    Instant lockedUntil = clock.instant()
                            .plus(DEFAULT_LOCK_DURATION);

                    account.recordLoginFailure(
                            DEFAULT_LOCK_THRESHOLD,
                            lockedUntil
                    );
                });
    }

    @Transactional
    public void recordLoginSuccess(String userId) {
        UserAccountEntity account = userAccountRepository
                .findByIdForUpdate(userId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "UserAccount",
                        userId
                ));

        account.resetLoginFailure();
    }

    @Transactional
    public void enableAccount(String userId) {
        getAccountForUpdate(userId).enable();
    }

    @Transactional
    public void disableAccount(String userId) {
        getAccountForUpdate(userId).disable();
    }

    @Transactional
    public void unlockAccount(String userId) {
        getAccountForUpdate(userId).resetLoginFailure();
    }

    @Transactional(readOnly = true)
    public boolean isLoginAllowed(String userId) {
        UserAccountEntity account = userAccountRepository
                .findById(userId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "UserAccount",
                        userId
                ));

        return account.isLoginAllowed(clock.instant());
    }

    private UserAccountEntity getAccountForUpdate(String userId) {
        return userAccountRepository
                .findByIdForUpdate(userId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "UserAccount",
                        userId
                ));
    }

    private void validateCreateCommand(
            CreateAccountCommand command
    ) {
        if (command == null) {
            throw new IllegalArgumentException(
                    "Create account command must not be null"
            );
        }

        requireText(command.email(), "email");
        requireText(command.displayName(), "displayName");
        validatePassword(command.rawPassword());
    }

    private void validatePassword(String password) {
        requireText(password, "password");

        if (password.length() < 12) {
            throw new IllegalArgumentException(
                    "Password must contain at least 12 characters"
            );
        }
    }

    private String normalizeEmail(String email) {
        return requireText(email, "email")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private String normalizeDisplayName(String value) {
        return requireText(value, "displayName").trim();
    }

    private String normalizeNullable(String value) {
        return value == null || value.isBlank()
                ? null
                : value.trim();
    }

    private String defaultIfBlank(
            String value,
            String defaultValue
    ) {
        return value == null || value.isBlank()
                ? defaultValue
                : value.trim();
    }

    private String requireText(
            String value,
            String field
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    field + " must not be blank"
            );
        }

        return value;
    }
}