package com.htv.farmverse.identity.mfa;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** Một mã khôi phục 2FA. Chỉ lưu bản băm; dùng một lần. */
@Getter
@Entity
@Table(name = "user_recovery_code")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecoveryCode {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "code_hash", nullable = false, length = 100)
    private String codeHash;

    @Column(name = "used_at")
    private Instant usedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Hai request dùng cùng một mã cùng lúc: request thứ hai thất bại khi ghi. */
    @Version
    private long version;

    public RecoveryCode(String userId, String codeHash) {
        this.id = UUID.randomUUID().toString();
        this.userId = userId;
        this.codeHash = codeHash;
    }

    public void markUsed(Instant now) {
        this.usedAt = now;
    }
}
