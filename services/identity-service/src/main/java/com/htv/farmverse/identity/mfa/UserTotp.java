package com.htv.farmverse.identity.mfa;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.persistence.EntityListeners;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** Authenticator TOTP của một tài khoản (tối đa một). */
@Getter
@Entity
@Table(name = "user_totp")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserTotp {

    @Id
    @Column(name = "user_id", length = 36)
    private String userId;

    @Column(name = "secret_cipher", nullable = false, length = 512)
    private String secretCipher;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "last_used_step")
    private Long lastUsedStep;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    public UserTotp(String userId, String secretCipher) {
        this.userId = userId;
        this.secretCipher = secretCipher;
    }

    public boolean isConfirmed() {
        return confirmedAt != null;
    }

    /** Bắt đầu lại đăng ký với secret mới (chưa xác nhận). */
    public void restart(String secretCipher) {
        this.secretCipher = secretCipher;
        this.confirmedAt = null;
        this.lastUsedStep = null;
    }

    public void confirm(Instant now, long step) {
        this.confirmedAt = now;
        this.lastUsedStep = step;
    }

    /** Mỗi bước 30 giây chỉ dùng một lần. */
    public boolean accept(long step) {
        if (lastUsedStep != null && step <= lastUsedStep) {
            return false;
        }
        lastUsedStep = step;
        return true;
    }
}
