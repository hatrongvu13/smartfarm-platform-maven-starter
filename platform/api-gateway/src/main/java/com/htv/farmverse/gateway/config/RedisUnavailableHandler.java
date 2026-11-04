package com.htv.farmverse.gateway.config;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;

import reactor.core.publisher.Mono;

/**
 * Redis (kho session + token) không trả lời → 503 ProblemDetail, fail-closed.
 *
 * <p>Không có phiên thì không có token, nên request có cookie phiên mà không đọc được Redis luôn bị chặn,
 * không bao giờ được chuyển xuống service. Lớp này chỉ đổi lỗi 500 chung thành 503 có {@code code} để SPA
 * phân biệt "hệ thống tạm lỗi" với "chưa đăng nhập".
 *
 * <p>Chạy trước {@code DefaultErrorWebExceptionHandler} của Boot (order -1). Lỗi khác đi tiếp như cũ.
 * Log chỉ ghi loại lỗi và đường dẫn, không ghi cookie, header hay thông điệp lỗi (có thể chứa địa chỉ nội bộ).
 *
 * <p>Lưu ý: {@code RequestRateLimiter} của Spring Cloud Gateway tự cho qua khi Redis lỗi (fail-open). Ở đây vẫn
 * an toàn vì route {@code /api/**} đòi đăng nhập, và phiên được nạp từ Redis trước bước giới hạn tốc độ.
 */
@Component
@Order(-3)   // trước DownstreamErrorHandler (-2): lỗi Redis cũng mang ConnectException trong cause
public class RedisUnavailableHandler implements WebExceptionHandler {

    public static final String CODE = GatewayProblems.UNAVAILABLE;

    private static final Logger log = LoggerFactory.getLogger(RedisUnavailableHandler.class);

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable error) {
        if (!isRedisFailure(error) || exchange.getResponse().isCommitted()) {
            return Mono.error(error);
        }
        log.warn("Redis không sẵn sàng ({}), trả 503 cho {}", rootType(error), exchange.getRequest().getPath());
        exchange.getResponse().getHeaders().set(HttpHeaders.RETRY_AFTER, "5");
        return GatewayProblems.write(exchange, HttpStatus.SERVICE_UNAVAILABLE, CODE,
                "Hệ thống phiên đăng nhập tạm thời không sẵn sàng, hãy thử lại sau ít phút");
    }

    static boolean isRedisFailure(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof DataAccessResourceFailureException   // gồm RedisConnectionFailureException
                    || t instanceof QueryTimeoutException          // hết thời gian chờ (khi Spring đã dịch lỗi)
                    || t instanceof RedisSystemException
                    // Kênh reactive của Lettuce ném thẳng lỗi gốc, không qua bộ dịch của Spring
                    // (vd RedisCommandTimeoutException khi Redis treo). Đã thấy bằng GatewayRedisIT.
                    || t instanceof io.lettuce.core.RedisException) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }

    private static String rootType(Throwable error) {
        Throwable t = error;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t.getClass().getSimpleName();
    }
}
