package com.htv.farmverse.gateway.config;

import java.net.InetSocketAddress;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import reactor.core.publisher.Mono;

/**
 * G5: giới hạn số lần bắt đầu đăng nhập ({@code /oauth2/authorization/**}) và callback ({@code /login/oauth2/code/**})
 * theo IP khách. Hai đường này không qua route của Spring Cloud Gateway nên {@code RequestRateLimiter} không áp được.
 *
 * <p>Vì sao: mỗi lần bắt đầu đăng nhập tạo một phiên trong Redis (lưu state, nonce, PKCE), và mỗi callback làm gateway
 * gọi identity đổi code. Không giới hạn thì một nguồn có thể làm ngập Redis hoặc dùng gateway để dội vào identity.
 * Giới hạn mật khẩu thật nằm ở identity ({@code POST /login}); filter này không thay nó.
 *
 * <p>Chạy TRƯỚC Spring Security (order -100) để chặn trước khi phiên được tạo. IP là {@code remoteAddress}: khi đi qua
 * edge tin cậy, {@link TrustedProxyForwardedHeaderTransformer} đã đặt nó thành IP khách; gọi thẳng thì là IP TCP,
 * nên {@code X-Forwarded-For} giả không né được. Vượt giới hạn: 429 ProblemDetail {@value GatewayProblems#TOO_MANY_REQUESTS}.
 * Log chỉ ghi đường dẫn, không ghi IP (dữ liệu cá nhân) hay tham số (có state/code).
 */
@Component
@Order(-150)
public class LoginRateLimitFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(LoginRateLimitFilter.class);
    private static final PathPattern[] LIMITED = {
            PathPatternParser.defaultInstance.parse("/oauth2/authorization/**"),
            PathPatternParser.defaultInstance.parse("/login/oauth2/code/**")
    };

    private final LoginAttemptCounter counter;
    private final int maxAttempts;
    private final Duration window;

    public LoginRateLimitFilter(LoginAttemptCounter counter,
                                @Value("${farmverse.gateway.login-rate-limit.max-attempts:20}") int maxAttempts,
                                @Value("${farmverse.gateway.login-rate-limit.window:1m}") Duration window) {
        this.counter = counter;
        this.maxAttempts = maxAttempts;
        this.window = window;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!limited(exchange.getRequest().getPath().pathWithinApplication())) {
            return chain.filter(exchange);
        }
        String ip = clientIp(exchange);
        return counter.increment(ip, window).flatMap(count -> {
            if (count <= maxAttempts) {
                return chain.filter(exchange);
            }
            log.warn("Quá giới hạn đăng nhập theo IP cho {}", exchange.getRequest().getPath());
            exchange.getResponse().getHeaders().set(HttpHeaders.RETRY_AFTER, String.valueOf(window.toSeconds()));
            return GatewayProblems.write(exchange, HttpStatus.TOO_MANY_REQUESTS, GatewayProblems.TOO_MANY_REQUESTS,
                    "Quá nhiều lần đăng nhập từ địa chỉ này, hãy thử lại sau ít phút");
        });
    }

    static boolean limited(PathContainer path) {
        for (PathPattern pattern : LIMITED) {
            if (pattern.matches(path)) {
                return true;
            }
        }
        return false;
    }

    static String clientIp(ServerWebExchange exchange) {
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        if (remote == null || remote.getAddress() == null) {
            return "unknown";
        }
        return remote.getAddress().getHostAddress();
    }
}
