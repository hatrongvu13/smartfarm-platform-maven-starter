package com.htv.farmverse.identity.oauth2;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

import com.htv.farmverse.identity.audit.SecurityAudit;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;

/**
 * Phát hiện dùng lại refresh token đã bị xoay (RFC 9700 §4.14.2, I1).
 *
 * <p>SAS xoay refresh token mỗi lần dùng ({@code reuseRefreshTokens=false}), nhưng token cũ chỉ đơn giản là
 * "không tìm thấy": kẻ đánh cắp token cũ bị từ chối, còn token mới (có thể cũng trong tay kẻ đó, hoặc của người
 * dùng thật) vẫn chạy 14 ngày. Lớp này bọc {@link OAuth2AuthorizationService}:
 * <ul>
 *   <li>Khi lưu một authorization có refresh token khác lần trước, ghi hash SHA-256 của token cũ vào
 *       {@code oauth2_rotated_refresh_token} (không lưu token thô).</li>
 *   <li>Khi tìm theo refresh token mà không thấy, tra bảng đó. Nếu là token đã bị xoay quá {@code grace} trước:
 *       có người đang dùng token cũ, nên xóa cả authorization (mọi token của lần cấp đó) và ghi nhật ký bảo mật.</li>
 * </ul>
 *
 * <p>{@code grace}: gateway có thể gửi hai lần refresh gần như cùng lúc với cùng một token (nhiều request song song
 * khi access token vừa hết hạn). Lần thứ hai trong khoảng này chỉ nhận {@code invalid_grant} như trước, không làm
 * người dùng bị đăng xuất. Ngoài khoảng này mới coi là đánh cắp.
 *
 * <p>Dòng của bảng bị xóa theo authorization ({@code ON DELETE CASCADE}).
 */
public final class RefreshTokenReuseDetection implements OAuth2AuthorizationService {

    private final OAuth2AuthorizationService delegate;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final Duration grace;

    public RefreshTokenReuseDetection(OAuth2AuthorizationService delegate, JdbcTemplate jdbc, Clock clock, Duration grace) {
        this.delegate = delegate;
        this.jdbc = jdbc;
        this.clock = clock;
        this.grace = grace;
    }

    @Override
    public void save(OAuth2Authorization authorization) {
        OAuth2Authorization.Token<OAuth2RefreshToken> next = authorization.getRefreshToken();
        if (next != null) {
            OAuth2Authorization previous = delegate.findById(authorization.getId());
            OAuth2Authorization.Token<OAuth2RefreshToken> old = previous == null ? null : previous.getRefreshToken();
            if (old != null && !old.getToken().getTokenValue().equals(next.getToken().getTokenValue())) {
                // Lưu authorization trước (khóa ngoại), rồi mới ghi token cũ
                delegate.save(authorization);
                recordRotated(authorization.getId(), old.getToken());
                return;
            }
        }
        delegate.save(authorization);
    }

    private void recordRotated(String authorizationId, OAuth2RefreshToken old) {
        Instant now = clock.instant();
        Instant expires = old.getExpiresAt() != null ? old.getExpiresAt() : now.plus(Duration.ofDays(30));
        try {
            jdbc.update("INSERT INTO oauth2_rotated_refresh_token (token_hash, authorization_id, rotated_at, expires_at) VALUES (?, ?, ?, ?)",
                    hash(old.getTokenValue()), authorizationId, Timestamp.from(now), Timestamp.from(expires));
        } catch (DuplicateKeyException ignored) {
            // đã ghi ở lần lưu trước của cùng lần xoay
        }
    }

    @Override
    public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
        OAuth2Authorization found = delegate.findByToken(token, tokenType);
        if (found != null || !(tokenType == null || OAuth2TokenType.REFRESH_TOKEN.equals(tokenType))) {
            return found;
        }
        List<Rotated> rotated = jdbc.query(
                "SELECT authorization_id, rotated_at FROM oauth2_rotated_refresh_token WHERE token_hash = ?",
                (rs, row) -> new Rotated(rs.getString(1), rs.getTimestamp(2).toInstant()), hash(token));
        if (rotated.isEmpty()) {
            return null;
        }
        Rotated hit = rotated.get(0);
        if (hit.rotatedAt().plus(grace).isAfter(clock.instant())) {
            return null;   // trong khoảng gần nhau: có thể là refresh song song của chính gateway
        }
        OAuth2Authorization family = delegate.findById(hit.authorizationId());
        if (family != null) {
            delegate.remove(family);
            SecurityAudit.warn("token.refresh_reuse_detected", "client", family.getRegisteredClientId(),
                    "principal", family.getPrincipalName(), "action", "authorization_revoked");
        }
        return null;
    }

    @Override
    public void remove(OAuth2Authorization authorization) {
        delegate.remove(authorization);
    }

    @Override
    public OAuth2Authorization findById(String id) {
        return delegate.findById(id);
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private record Rotated(String authorizationId, Instant rotatedAt) {
    }
}
