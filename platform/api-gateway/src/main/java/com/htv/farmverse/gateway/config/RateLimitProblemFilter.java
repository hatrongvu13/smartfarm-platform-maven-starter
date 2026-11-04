package com.htv.farmverse.gateway.config;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

/**
 * {@code RequestRateLimiter} của Spring Cloud Gateway chỉ đặt 429 rồi kết thúc phản hồi rỗng ({@code setComplete()}),
 * không có chỗ cấu hình body. Filter này bọc phản hồi: khi phản hồi kết thúc rỗng với 429, thêm body ProblemDetail
 * {@value GatewayProblems#TOO_MANY_REQUESTS}. Phản hồi khác, và 429 đã có body (ví dụ do service phía sau trả), giữ nguyên.
 */
@Component
@Order(0)
public class RateLimitProblemFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpResponseDecorator response = new ServerHttpResponseDecorator(exchange.getResponse()) {
            @Override
            public Mono<Void> setComplete() {
                if (HttpStatus.TOO_MANY_REQUESTS.equals(getStatusCode()) && !isCommitted()) {
                    return GatewayProblems.write(getDelegate(), HttpStatus.TOO_MANY_REQUESTS,
                            GatewayProblems.TOO_MANY_REQUESTS, "Quá nhiều yêu cầu, hãy thử lại sau ít giây");
                }
                return super.setComplete();
            }
        };
        return chain.filter(exchange.mutate().response(response).build());
    }
}
