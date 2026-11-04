package com.htv.farmverse.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

class RedisUnavailableHandlerTest {

    private final RedisUnavailableHandler handler = new RedisUnavailableHandler();

    @Test
    void recognisesRedisFailuresAnywhereInTheCauseChain() {
        assertThat(RedisUnavailableHandler.isRedisFailure(new RedisConnectionFailureException("x"))).isTrue();
        assertThat(RedisUnavailableHandler.isRedisFailure(new QueryTimeoutException("x"))).isTrue();
        assertThat(RedisUnavailableHandler.isRedisFailure(
                new IllegalStateException("wrap", new RedisConnectionFailureException("x")))).isTrue();
    }

    /** Lỗi khác không bị nuốt thành 503: đi tiếp tới bộ xử lý mặc định. */
    @Test
    void otherErrorsPassThrough() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/x"));
        IllegalStateException error = new IllegalStateException("boom");

        StepVerifier.create(handler.handle(exchange, error)).expectErrorMatches(e -> e == error).verify();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }
}
