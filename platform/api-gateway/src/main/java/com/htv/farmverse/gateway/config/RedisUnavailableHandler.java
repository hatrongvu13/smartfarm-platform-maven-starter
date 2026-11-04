package com.htv.farmverse.gateway.config;

import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
@Order(-2)
public class RedisUnavailableHandler implements WebExceptionHandler {

    public static final String CODE = "SF-GATEWAY-503";

    private static final Logger log = LoggerFactory.getLogger(RedisUnavailableHandler.class);

    private static final byte[] BODY = ("{\"type\":\"urn:farmverse:problem:" + CODE + "\","
            + "\"title\":\"Service Unavailable\",\"status\":503,"
            + "\"detail\":\"Hệ thống phiên đăng nhập tạm thời không sẵn sàng, hãy thử lại sau ít phút\","
            + "\"code\":\"" + CODE + "\"}").getBytes(StandardCharsets.UTF_8);

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable error) {
        if (!isRedisFailure(error) || exchange.getResponse().isCommitted()) {
            return Mono.error(error);
        }
        log.warn("Redis không sẵn sàng ({}), trả 503 cho {}", rootType(error), exchange.getRequest().getPath());
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        response.getHeaders().set(HttpHeaders.RETRY_AFTER, "5");
        response.getHeaders().setCacheControl("no-store");
        DataBuffer buffer = response.bufferFactory().wrap(BODY);
        return response.writeWith(Mono.just(buffer));
    }

    static boolean isRedisFailure(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof DataAccessResourceFailureException   // gồm RedisConnectionFailureException
                    || t instanceof QueryTimeoutException          // Lettuce hết thời gian chờ lệnh
                    || t instanceof RedisSystemException) {
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
