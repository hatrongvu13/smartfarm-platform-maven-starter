package com.htv.farmverse.gateway.config;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;

import io.netty.channel.ConnectTimeoutException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.PrematureCloseException;

/**
 * Lỗi khi gateway gọi service phía sau → ProblemDetail, thay cho 500 chung của Boot:
 * <ul>
 *   <li>504 {@value GatewayProblems#GATEWAY_TIMEOUT}: quá {@code response-timeout} (Spring Cloud Gateway ném
 *       {@code ResponseStatusException(GATEWAY_TIMEOUT)}) hoặc quá {@code connect-timeout}.</li>
 *   <li>502 {@value GatewayProblems#BAD_GATEWAY}: service từ chối kết nối, không phân giải được tên, đóng kết nối giữa chừng.</li>
 * </ul>
 * Log ghi loại lỗi và route, không ghi thông điệp (chứa địa chỉ nội bộ). Lỗi khác đi tiếp như cũ.
 */
@Component
@Order(-2)
public class DownstreamErrorHandler implements WebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(DownstreamErrorHandler.class);

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable error) {
        HttpStatus status = classify(error);
        if (status == null || exchange.getResponse().isCommitted()) {
            return Mono.error(error);
        }
        log.warn("Service phía sau lỗi ({}), trả {} cho {}", rootType(error), status.value(), exchange.getRequest().getPath());
        return status == HttpStatus.GATEWAY_TIMEOUT
                ? GatewayProblems.write(exchange, status, GatewayProblems.GATEWAY_TIMEOUT,
                        "Dịch vụ phía sau không trả lời kịp, hãy thử lại")
                : GatewayProblems.write(exchange, status, GatewayProblems.BAD_GATEWAY,
                        "Dịch vụ phía sau tạm thời không kết nối được, hãy thử lại");
    }

    static HttpStatus classify(Throwable error) {
        // Redis (kho phiên) cũng lỗi bằng ConnectException; đó là 503 của RedisUnavailableHandler, không phải 502.
        if (RedisUnavailableHandler.isRedisFailure(error)) {
            return null;
        }
        if (error instanceof ResponseStatusException rse && rse.getStatusCode().value() == 504) {
            return HttpStatus.GATEWAY_TIMEOUT;
        }
        for (Throwable t = error; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof ConnectTimeoutException || t instanceof TimeoutException) {
                return HttpStatus.GATEWAY_TIMEOUT;
            }
            if (t instanceof ConnectException || t instanceof UnknownHostException || t instanceof PrematureCloseException) {
                return HttpStatus.BAD_GATEWAY;
            }
        }
        return null;
    }

    private static String rootType(Throwable error) {
        Throwable t = error;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t.getClass().getSimpleName();
    }
}
