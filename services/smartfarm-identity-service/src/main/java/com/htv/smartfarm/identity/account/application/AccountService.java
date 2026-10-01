package com.htv.smartfarm.identity.account.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import com.htv.smartfarm.common.exception.ConflictException;
import com.htv.smartfarm.common.exception.NotFoundException;
import com.htv.smartfarm.common.exception.ValidationException;
import com.htv.smartfarm.identity.account.application.command.CreateAccountCommand;
import com.htv.smartfarm.identity.account.domain.UserAccountEntity;
import com.htv.smartfarm.identity.account.domain.UserProfileEntity;
import com.htv.smartfarm.identity.account.repository.UserAccountRepository;
import com.htv.smartfarm.identity.account.repository.UserProfileRepository;
import com.htv.smartfarm.identity.token.RefreshRepository;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private static final int DEFAULT_LOCK_THRESHOLD = 5;
    private static final Duration DEFAULT_LOCK_DURATION = Duration.ofMinutes(15);
    private static final int MINIMUM_PASSWORD_LENGTH = 12;
    private static final int MAXIMUM_PASSWORD_LENGTH = 128;
    private static final String DEFAULT_LOCALE = "vi-VN";
    private static final String DEFAULT_TIME_ZONE = "Asia/Ho_Chi_Minh";

    private final UserAccountRepository userAccountRepository;
    private final UserProfileRepository userProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final RefreshRepository refreshRepository;

    public AccountService(
            UserAccountRepository userAccountRepository,
            UserProfileRepository userProfileRepository,
            PasswordEncoder passwordEncoder,
            Clock clock,
            RefreshRepository refreshRepository
    ) {
        this.userAccountRepository = userAccountRepository;
        this.userProfileRepository = userProfileRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.refreshRepository = refreshRepository;
    }

    @Transactional
    public String createAccount(CreateAccountCommand command) {
        validateCreateCommand(command);

        String email = command.email().trim();
        String normalizedEmail = normalizeEmail(email);

        if (userAccountRepository.existsByNormalizedEmail(normalizedEmail)) {
            throw new ConflictException(
                    "EMAIL_ALREADY_ASSIGNED",
                    "Email is already assigned to another account"
            );
        }

        String userId = UUID.randomUUID().toString();
        String displayName = normalizeDisplayName(command.displayName());
        String locale = defaultIfBlank(command.locale(), DEFAULT_LOCALE);
        String timeZone = defaultIfBlank(command.timeZone(), DEFAULT_TIME_ZONE);

        UserAccountEntity account = new UserAccountEntity(
                userId,
                email,
                passwordEncoder.encode(command.rawPassword())
        );

        UserProfileEntity profile = new UserProfileEntity(
                account,
                displayName,
                locale,
                timeZone
        );

        profile.update(
                displayName,
                normalizeNullable(command.phoneNumber()),
                locale,
                timeZone
        );

        userAccountRepository.saveAndFlush(account);
        userProfileRepository.saveAndFlush(profile);

        return userId;
    }

    @Transactional
    public void changePassword(
            String userId,
            String currentRawPassword,
            String newRawPassword
    ) {
        requireText(userId, "userId");
        requireText(currentRawPassword, "currentRawPassword");
        validatePassword(newRawPassword);

        UserAccountEntity account = getAccountForUpdate(userId);

        if (!passwordEncoder.matches(
                currentRawPassword,
                account.getPasswordHash()
        )) {
            throw new ValidationException(
                    "CURRENT_PASSWORD_INVALID",
                    "Current password is invalid"
            );
        }

        if (passwordEncoder.matches(
                newRawPassword,
                account.getPasswordHash()
        )) {
            throw new ConflictException(
                    "PASSWORD_REUSE_NOT_ALLOWED",
                    "New password must differ from current password"
            );
        }

        account.changePasswordHash(
                passwordEncoder.encode(newRawPassword)
        );
        refreshRepository.revokeUser(userId);
    }

    @Transactional
    public void resetPassword(
            String userId,
            String newRawPassword
    ) {
        requireText(userId, "userId");
        validatePassword(newRawPassword);

        UserAccountEntity account = getAccountForUpdate(userId);
        account.changePasswordHash(passwordEncoder.encode(newRawPassword));
        refreshRepository.revokeUser(userId);
    }

    @Transactional
    public void recordLoginFailure(String email) {
        String normalizedEmail = normalizeEmail(email);

        userAccountRepository
                .findByNormalizedEmailForUpdate(normalizedEmail)
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
        getAccountForUpdate(userId).resetLoginFailure();
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
        requireText(userId, "userId");

        UserAccountEntity account = userAccountRepository
                .findById(userId)
                .orElseThrow(() -> NotFoundException.entity(
                        "UserAccount",
                        userId
                ));

        return account.isLoginAllowed(clock.instant());
    }

    private UserAccountEntity getAccountForUpdate(String userId) {
        requireText(userId, "userId");

        return userAccountRepository
                .findByIdForUpdate(userId)
                .orElseThrow(() -> NotFoundException.entity(
                        "UserAccount",
                        userId
                ));
    }

    private void validateCreateCommand(CreateAccountCommand command) {
        if (command == null) {
            throw new ValidationException(
                    "CREATE_ACCOUNT_COMMAND_REQUIRED",
                    "Create account command must not be null"
            );
        }

        requireText(command.email(), "email");
        requireText(command.displayName(), "displayName");
        validatePassword(command.rawPassword());
    }

    private void validatePassword(String password) {
        requireText(password, "password");

        if (password.length() < MINIMUM_PASSWORD_LENGTH) {
            throw new ValidationException(
                    "PASSWORD_TOO_SHORT",
                    "Password must contain at least "
                            + MINIMUM_PASSWORD_LENGTH
                            + " characters"
            );
        }

        if (password.length() > MAXIMUM_PASSWORD_LENGTH) {
            throw new ValidationException(
                    "PASSWORD_TOO_LONG",
                    "Password must not exceed "
                            + MAXIMUM_PASSWORD_LENGTH
                            + " characters"
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

    private String defaultIfBlank(String value, String defaultValue) {
        return value == null || value.isBlank()
                ? defaultValue
                : value.trim();
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(
                    "REQUIRED_FIELD_MISSING",
                    field + " must not be blank"
            );
        }

        return value;
    }
}
