package com.htv.farmverse.identity.login;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

import com.htv.farmverse.service.web.ProblemResponses;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Giới hạn số lần thử đăng nhập theo địa chỉ IP: {@code POST /login} và {@code POST /login/totp}.
 * Bổ sung cho khóa tạm theo tài khoản: khóa theo tài khoản chặn dò một tài khoản,
 * giới hạn theo IP chặn một nguồn thử nhiều tài khoản.
 *
 * <p>Cửa sổ cố định, đếm trong bộ nhớ của một instance. Chạy nhiều instance thì mỗi instance đếm riêng
 * (giới hạn thực tế lớn hơn); khi cần chính xác, chuyển bộ đếm sang Redis.
 * Dùng {@code getRemoteAddr()}, không tin {@code X-Forwarded-For} vì trình duyệt gọi thẳng identity.
 */
public final class LoginRateLimitFilter extends OncePerRequestFilter {

    /** Tham số trên URL trang đăng nhập: số giây còn phải đợi. */
    public static final String LIMITED_PARAM = "limited";

    private static final int MAX_TRACKED_ADDRESSES = 10_000;

    private final int maxAttempts;
    private final long windowMillis;
    private final Clock clock;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public LoginRateLimitFilter(int maxAttempts, Duration window, Clock clock) {
        this.maxAttempts = maxAttempts;
        this.windowMillis = window.toMillis();
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"POST".equals(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !("/login".equals(path) || TotpLogin.PAGE.equals(path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long now = clock.millis();
        if (windows.size() > MAX_TRACKED_ADDRESSES) {
            windows.values().removeIf(window -> window.resetAt() <= now);
        }
        Window window = windows.compute(request.getRemoteAddr(),
                (address, current) -> current == null || current.resetAt() <= now
                        ? new Window(now + windowMillis, 1)
                        : new Window(current.resetAt(), current.count() + 1));
        if (window.count() > maxAttempts) {
            long retryAfterSeconds = Math.max(1, (window.resetAt() - now + 999) / 1000);
            response.setHeader("Retry-After", Long.toString(retryAfterSeconds));
            if (isBrowserForm(request)) {
                // Form HTML: đưa người dùng về lại trang kèm thông báo thay vì hiện JSON thô.
                String page = request.getRequestURI().substring(request.getContextPath().length());
                response.setStatus(HttpServletResponse.SC_SEE_OTHER);
                response.setHeader("Location", request.getContextPath() + page + "?" + LIMITED_PARAM + "=" + retryAfterSeconds);
                return;
            }
            ProblemResponses.write(response, 429, "Too Many Requests", ProblemResponses.TOO_MANY_REQUESTS,
                    "Thử đăng nhập quá nhiều lần, hãy đợi rồi thử lại");
            return;
        }
        chain.doFilter(request, response);
    }

    /** Trình duyệt gửi form: header Accept có text/html. Client API (JSON) vẫn nhận 429 ProblemDetail. */
    private static boolean isBrowserForm(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains("text/html");
    }

    private record Window(long resetAt, int count) {
    }
}
