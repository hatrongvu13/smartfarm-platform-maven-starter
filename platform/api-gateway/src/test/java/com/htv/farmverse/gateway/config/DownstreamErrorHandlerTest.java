package com.htv.farmverse.gateway.config;

import java.net.ConnectException;
import java.net.UnknownHostException;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;

class DownstreamErrorHandlerTest {

    @Test
    void classifiesDownstreamFailures() {
        assertThat(DownstreamErrorHandler.classify(new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT)))
                .isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(DownstreamErrorHandler.classify(new io.netty.channel.ConnectTimeoutException("x")))
                .isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(DownstreamErrorHandler.classify(new IllegalStateException(new ConnectException("refused"))))
                .isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(DownstreamErrorHandler.classify(new UnknownHostException("farm")))
                .isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(DownstreamErrorHandler.classify(new org.springframework.cloud.gateway.support.ServiceUnavailableException()))
                .as("circuit breaker đang mở").isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    /** Hồi quy đã gặp: lỗi Redis chứa ConnectException; phải để cho RedisUnavailableHandler (503). */
    @Test
    void redisFailuresAreNotDownstreamFailures() {
        assertThat(DownstreamErrorHandler.classify(
                new RedisConnectionFailureException("x", new ConnectException("refused")))).isNull();
    }

    @Test
    void otherErrorsAreLeftAlone() {
        assertThat(DownstreamErrorHandler.classify(new IllegalArgumentException("boom"))).isNull();
        assertThat(DownstreamErrorHandler.classify(new ResponseStatusException(HttpStatus.NOT_FOUND))).isNull();
    }
}
