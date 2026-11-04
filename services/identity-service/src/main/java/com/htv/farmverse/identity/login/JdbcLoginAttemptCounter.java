package com.htv.farmverse.identity.login;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Bộ đếm dùng chung giữa mọi instance identity, lưu ở bảng {@code login_attempt_window} của PostgreSQL (M8).
 *
 * <p>Một câu lệnh {@code INSERT ... ON CONFLICT DO UPDATE ... RETURNING}: PostgreSQL khóa dòng khi cập nhật,
 * nên nhiều request cùng lúc (từ nhiều instance) không làm mất lần đếm nào. Thời gian lấy từ {@code now()} của
 * database, không từ đồng hồ của từng instance, nên các instance lệch giờ vẫn chung một cửa sổ.
 *
 * <p>Không cần Redis: identity đã có PostgreSQL, và đăng nhập vốn cần database. Database lỗi thì đăng nhập
 * cũng không chạy được, nên {@link LoginRateLimitFilter} trả 503 (fail-closed), không bỏ qua giới hạn.
 *
 * <p>Dòng hết hạn bị xóa định kỳ, mỗi {@value #CLEANUP_EVERY} lần gọi. Bảng chỉ giữ địa chỉ IP trong vài phút.
 */
public final class JdbcLoginAttemptCounter implements LoginAttemptCounter {

    static final int CLEANUP_EVERY = 1000;

    private static final String HIT = """
            INSERT INTO login_attempt_window AS w (bucket, attempts, reset_at)
            VALUES (?, 1, now() + make_interval(secs => ?))
            ON CONFLICT (bucket) DO UPDATE SET
                attempts = CASE WHEN w.reset_at <= now() THEN 1 ELSE w.attempts + 1 END,
                reset_at = CASE WHEN w.reset_at <= now() THEN EXCLUDED.reset_at ELSE w.reset_at END
            RETURNING attempts, ceil(extract(epoch FROM w.reset_at - now()))::bigint AS retry_after
            """;

    private final JdbcTemplate jdbc;
    private final AtomicLong calls = new AtomicLong();

    public JdbcLoginAttemptCounter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Attempt hit(String key, Duration window) {
        if (calls.incrementAndGet() % CLEANUP_EVERY == 0) {
            deleteExpired();
        }
        double seconds = window.toMillis() / 1000.0;
        return jdbc.queryForObject(HIT, (rs, row) -> new Attempt(rs.getInt("attempts"), Math.max(1, rs.getLong("retry_after"))),
                key, seconds);
    }

    /** Xóa các cửa sổ đã kết thúc. */
    public int deleteExpired() {
        return jdbc.update("DELETE FROM login_attempt_window WHERE reset_at <= now()");
    }
}
