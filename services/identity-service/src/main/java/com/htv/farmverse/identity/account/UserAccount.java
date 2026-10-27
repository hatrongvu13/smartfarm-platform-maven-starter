package com.htv.farmverse.identity.account;

import java.time.Instant;
import java.util.Locale;

import com.htv.farmverse.identity.shared.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Tài khoản đăng nhập toàn cục (một email = một account), độc lập với tenant. */
@Getter
@Entity
@Table(name = "user_account")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserAccount extends BaseEntity {

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "normalized_email", nullable = false, unique = true, length = 254)
    private String normalizedEmail;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 150)
    private String displayName;

    @Column(nullable = false, length = 20)
    private String locale;

    @Column(name = "time_zone", nullable = false, length = 50)
    private String timeZone;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    public UserAccount(String email, String passwordHash, String displayName) {
        this.email = email.trim();
        this.normalizedEmail = normalize(email);
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.locale = "vi-VN";
        this.timeZone = "Asia/Ho_Chi_Minh";
    }

    public static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    public void updateProfile(String displayName, String locale, String timeZone) {
        if (displayName != null) this.displayName = displayName;
        if (locale != null) this.locale = locale;
        if (timeZone != null) this.timeZone = timeZone;
    }

    public void changePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void loginSucceeded(Instant now) {
        failedAttempts = 0;
        lockedUntil = null;
        lastLoginAt = now;
    }

    /** Đếm lần sai; tới ngưỡng thì khóa tạm và đếm lại từ đầu. */
    public void loginFailed(Instant now, int maxFailures, java.time.Duration lockDuration) {
        failedAttempts++;
        if (failedAttempts >= maxFailures) {
            lockedUntil = now.plus(lockDuration);
            failedAttempts = 0;
        }
    }

    public void unlock() {
        failedAttempts = 0;
        lockedUntil = null;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
