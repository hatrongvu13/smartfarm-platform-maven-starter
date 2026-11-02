package com.htv.farmverse.identity.account;

import java.time.Clock;
import java.time.Instant;

import com.htv.farmverse.identity.audit.SecurityAudit;
import com.htv.farmverse.identity.config.IdentityProperties;
import com.htv.farmverse.identity.shared.IdentityErrors;
import com.htv.farmverse.service.web.ApiProblem;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Vòng đời tài khoản: tạo, hồ sơ, mật khẩu, khóa tạm khi đăng nhập sai. */
@Service
@RequiredArgsConstructor
public class AccountService {

    private final UserAccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final IdentityProperties properties;
    private final Clock clock;

    @Transactional
    public UserAccount create(String email, String rawPassword, String displayName) {
        if (accounts.existsByNormalizedEmail(UserAccount.normalize(email))) {
            throw ApiProblem.conflict(IdentityErrors.EMAIL_TAKEN, "Email đã được dùng");
        }
        return accounts.save(new UserAccount(email, passwordEncoder.encode(rawPassword), displayName));
    }

    @Transactional(readOnly = true)
    public UserAccount get(String userId) {
        return accounts.findById(userId)
                .orElseThrow(() -> ApiProblem.notFound(IdentityErrors.ACCOUNT_NOT_FOUND, "Không có tài khoản"));
    }

    @Transactional
    public UserAccount updateProfile(String userId, String displayName, String locale, String timeZone) {
        UserAccount account = get(userId);
        account.updateProfile(displayName, locale, timeZone);
        return account;
    }

    @Transactional
    public void changePassword(String userId, String currentPassword, String newPassword) {
        UserAccount account = get(userId);
        if (!passwordEncoder.matches(currentPassword, account.getPasswordHash())) {
            throw new ApiProblem(HttpStatus.BAD_REQUEST, IdentityErrors.WRONG_PASSWORD, "Mật khẩu hiện tại không đúng");
        }
        account.changePasswordHash(passwordEncoder.encode(newPassword));
    }

    @Transactional
    public void unlock(String userId) {
        get(userId).unlock();
    }

    @Transactional
    public void recordLoginSuccess(String userId) {
        accounts.findById(userId).ifPresent(account -> account.loginSucceeded(clock.instant()));
    }

    /** Gọi khi sai mã TOTP: dùng chung bộ đếm khóa tạm với sai mật khẩu. */
    @Transactional
    public void recordFactorFailure(String userId) {
        accounts.findById(userId).ifPresent(this::failed);
    }

    /** Gọi khi sai mật khẩu; email không tồn tại thì bỏ qua (không lộ thông tin). */
    @Transactional
    public void recordLoginFailure(String email) {
        accounts.findByNormalizedEmail(UserAccount.normalize(email)).ifPresent(this::failed);
    }

    /** Đếm lần sai. Ghi nhật ký đúng lúc tài khoản chuyển từ mở sang bị khóa, không ghi lại ở các lần sai sau. */
    private void failed(UserAccount account) {
        Instant now = clock.instant();
        boolean wasLocked = account.isLocked(now);
        account.loginFailed(now, properties.lockout().maxFailures(), properties.lockout().duration());
        if (!wasLocked && account.isLocked(now)) {
            SecurityAudit.warn("account.locked", "actor", "system", "target", account.getId(),
                    "duration", properties.lockout().duration());
        }
    }
}
