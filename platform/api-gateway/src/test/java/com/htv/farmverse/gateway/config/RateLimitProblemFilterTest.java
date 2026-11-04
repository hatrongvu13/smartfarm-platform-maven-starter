package com.htv.farmverse.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code RequestRateLimiter} của Spring Cloud Gateway chỉ đặt 429 rồi kết thúc phản hồi rỗng. Filter phải thêm
 * body ProblemDetail, mà không đụng tới phản hồi bình thường. (Không test qua route thật vì bộ giới hạn cần Redis.)
 */
class RateLimitProblemFilterTest {

    private final RateLimitProblemFilter filter = new RateLimitProblemFilter();

    private static MockServerWebExchange run(WebFilterChain downstream) {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/farm/farms"));
        StepVerifier.create(new RateLimitProblemFilter().filter(exchange, downstream)).verifyComplete();
        return exchange;
    }

    @Test
    void emptyTooManyRequestsGetsAProblemBody() {
        MockServerWebExchange exchange = run(ex -> {
            ex.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);   // đúng như RequestRateLimiter làm
            return ex.getResponse().setComplete();
        });

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(exchange.getResponse().getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(exchange.getResponse().getBodyAsString().block())
                .contains("\"status\":429").contains("\"code\":\"SF-GATEWAY-429\"");
    }

    @Test
    void normalResponsesAreUntouched() {
        MockServerWebExchange exchange = run(ex -> {
            ex.getResponse().setStatusCode(HttpStatus.NO_CONTENT);
            return ex.getResponse().setComplete();
        });

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(exchange.getResponse().getHeaders().getContentType()).isNull();
    }

    @Test
    void downstreamBodyIsKeptEvenFor429() {
        MockServerWebExchange exchange = run(ex -> {
            ex.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            return ex.getResponse().writeWith(Mono.just(ex.getResponse().bufferFactory().wrap("own".getBytes())));
        });

        assertThat(exchange.getResponse().getBodyAsString().block()).isEqualTo("own");
        assertThat(filter).isNotNull();
    }
}
