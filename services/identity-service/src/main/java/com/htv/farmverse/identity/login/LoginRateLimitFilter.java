package com.htv.farmverse.identity.login;

import java.io.IOException;
import java.time.Duration;

import com.htv.farmverse.identity.audit.SecurityAudit;
import com.htv.farmverse.identity.shared.IdentityErrors;
import com.htv.farmverse.service.web.ProblemResponses;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Giới hạn số lần thử đăng nhập theo địa chỉ IP: {@code POST /login} và {@code POST /login/totp}.
 * Bổ sung cho khóa tạm theo tài khoản: khóa theo tài khoản chặn dò một tài khoản,
 * giới hạn theo IP chặn một nguồn thử nhiều tài khoản.
 *
 * <p>Cửa sổ cố định. Bộ đếm ({@link LoginAttemptCounter}) mặc định ở PostgreSQL, dùng chung giữa mọi instance (M8).
 * Bộ đếm lỗi thì trả 503 và không cho thử đăng nhập (fail-closed).
 * Dùng {@code getRemoteAddr()}, không tin {@code X-Forwarded-For} vì trình duyệt gọi thẳng identity.
 */
public final class LoginRateLimitFilter extends OncePerRequestFilter {

    /** Tham số trên URL trang đăng nhập: số giây còn phải đợi. */
    public static final String LIMITED_PARAM = "limited";

    private static final Logger log = LoggerFactory.getLogger(LoginRateLimitFilter.class);

    private final int maxAttempts;
    private final Duration window;
    private final LoginAttemptCounter counter;

    public LoginRateLimitFilter(int maxAttempts, Duration window, LoginAttemptCounter counter) {
        this.maxAttempts = maxAttempts;
        this.window = window;
        this.counter = counter;
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
        LoginAttemptCounter.Attempt attempt;
        try {
            attempt = counter.hit("login:" + request.getRemoteAddr(), window);
        } catch (DataAccessException e) {
            log.warn("Login attempt counter unavailable, rejecting login: {}", e.getClass().getSimpleName());
            ProblemResponses.write(response, 503, "Service Unavailable", IdentityErrors.LOGIN_UNAVAILABLE,
                    "Hệ thống đăng nhập đang bận, hãy thử lại sau");
            return;
        }
        if (attempt.count() > maxAttempts) {
            if (attempt.count() == maxAttempts + 1) {
                SecurityAudit.warn("login.rate_limited", "address", request.getRemoteAddr(),
                        "retry_after", attempt.retryAfterSeconds());
            }
            response.setHeader("Retry-After", Long.toString(attempt.retryAfterSeconds()));
            if (isBrowserForm(request)) {
                // Form HTML: đưa người dùng về lại trang kèm thông báo thay vì hiện JSON thô.
                String page = request.getRequestURI().substring(request.getContextPath().length());
                response.setStatus(HttpServletResponse.SC_SEE_OTHER);
                response.setHeader("Location", request.getContextPath() + page + "?" + LIMITED_PARAM + "=" + attempt.retryAfterSeconds());
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
}
