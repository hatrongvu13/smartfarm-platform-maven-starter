package com.htv.farmverse.gateway.config;

import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * Một chỗ duy nhất gateway viết lỗi của chính nó: RFC 9457 {@code application/problem+json} có thêm {@code code},
 * cùng dạng với {@code ApiProblem} / {@code ProblemResponses} của các service, để SPA chỉ xử lý một kiểu.
 *
 * <p>Thông điệp là hằng số do gateway đặt, không bao giờ chép thông điệp của exception (có thể chứa địa chỉ nội bộ)
 * hay dữ liệu từ request. Vì vậy JSON được dựng bằng chuỗi, không cần escape.
 */
public final class GatewayProblems {

    public static final String UNAUTHORIZED = "SF-AUTH-001";
    public static final String FORBIDDEN = "SF-AUTH-002";
    public static final String TOO_MANY_REQUESTS = "SF-GATEWAY-429";
    public static final String BAD_GATEWAY = "SF-GATEWAY-502";
    public static final String UNAVAILABLE = "SF-GATEWAY-503";
    public static final String GATEWAY_TIMEOUT = "SF-GATEWAY-504";

    private GatewayProblems() {
    }

    public static Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String code, String detail) {
        return write(exchange.getResponse(), status, code, detail);
    }

    public static Mono<Void> write(ServerHttpResponse response, HttpStatus status, String code, String detail) {
        if (response.isCommitted()) {
            return Mono.empty();
        }
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        response.getHeaders().setCacheControl("no-store");
        byte[] body = json(status, code, detail).getBytes(StandardCharsets.UTF_8);
        response.getHeaders().setContentLength(body.length);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }

    static String json(HttpStatus status, String code, String detail) {
        return "{\"type\":\"urn:farmverse:problem:" + code + "\",\"title\":\"" + status.getReasonPhrase()
                + "\",\"status\":" + status.value() + ",\"detail\":\"" + detail + "\",\"code\":\"" + code + "\"}";
    }
}
