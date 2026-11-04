package com.htv.farmverse.gateway.config;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class TrustedProxyForwardedHeaderTransformerTest {

    private static boolean trusts(String cidrs, String address) throws Exception {
        return new TrustedProxyForwardedHeaderTransformer(List.of(cidrs.split(",")))
                .trusts(InetAddress.getByName(address));
    }

    @Test
    void cidrMatching() throws Exception {
        assertThat(trusts("10.0.0.0/8", "10.20.30.40")).isTrue();
        assertThat(trusts("10.0.0.0/8", "11.0.0.1")).isFalse();
        assertThat(trusts("192.168.1.0/25", "192.168.1.127")).isTrue();
        assertThat(trusts("192.168.1.0/25", "192.168.1.128")).isFalse();
        assertThat(trusts("172.16.0.5", "172.16.0.5")).isTrue();      // không có /n = đúng một địa chỉ
        assertThat(trusts("172.16.0.5", "172.16.0.6")).isFalse();
        assertThat(trusts("fd00::/8", "fd12:3456::1")).isTrue();
        assertThat(trusts("fd00::/8", "fe80::1")).isFalse();
        assertThat(trusts("10.0.0.0/8", "::ffff:0a00:0001")).isTrue(); // IPv4-mapped: Java trả về Inet4Address
        assertThat(trusts("0.0.0.0/0", "8.8.8.8")).isTrue();
        assertThat(trusts("10.0.0.0/8, 127.0.0.1/32", "127.0.0.1")).isTrue();
    }

    @Test
    void prodWithoutTrustedProxiesRefusesToStart() {
        var prod = new org.springframework.mock.env.MockEnvironment();
        prod.setActiveProfiles("prod");
        assertThatIllegalStateExceptionThrownBy(() -> new SecurityConfiguration().forwardedHeaderTransformer(List.of(), prod));
        assertThat(new SecurityConfiguration().forwardedHeaderTransformer(List.of("10.0.0.0/8"), prod)).isNotNull();
        assertThat(new SecurityConfiguration().forwardedHeaderTransformer(List.of(), new org.springframework.mock.env.MockEnvironment()))
                .as("dev/test: rỗng là bình thường").isNotNull();
    }

    private static void assertThatIllegalStateExceptionThrownBy(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        org.assertj.core.api.Assertions.assertThatIllegalStateException().isThrownBy(call)
                .withMessageContaining("GATEWAY_TRUSTED_PROXIES");
    }

    @Test
    void emptyListTrustsNobody() throws Exception {
        assertThat(new TrustedProxyForwardedHeaderTransformer(List.of()).trusts(InetAddress.getByName("127.0.0.1")))
                .isFalse();
    }

    @Test
    void invalidEntriesFailAtStartup() {
        assertThatIllegalArgumentException().isThrownBy(() -> new TrustedProxyForwardedHeaderTransformer(List.of("edge.internal")));
        assertThatIllegalArgumentException().isThrownBy(() -> new TrustedProxyForwardedHeaderTransformer(List.of("10.0.0.0/33")));
    }

    @Test
    void untrustedSourceHasForwardedHeadersRemoved() {
        ServerHttpRequest request = MockServerHttpRequest.get("http://gw.internal/x")
                .remoteAddress(new InetSocketAddress("203.0.113.9", 4000))
                .header("X-Forwarded-Host", "evil.test").header("X-Forwarded-Proto", "https")
                .header("Forwarded", "host=evil.test").build();

        ServerHttpRequest result = new TrustedProxyForwardedHeaderTransformer(List.of("10.0.0.0/8")).apply(request);

        assertThat(result.getURI().toString()).isEqualTo("http://gw.internal/x");
        assertThat(result.getHeaders().containsHeader("X-Forwarded-Host")).isFalse();
        assertThat(result.getHeaders().containsHeader("Forwarded")).isFalse();
    }

    @Test
    void trustedSourceRewritesSchemeAndHost() {
        ServerHttpRequest request = MockServerHttpRequest.get("http://gw.internal/x")
                .remoteAddress(new InetSocketAddress("10.1.2.3", 4000))
                .header("X-Forwarded-Host", "farm.example.test").header("X-Forwarded-Proto", "https")
                .header("X-Forwarded-Prefix", "/hijack").build();

        ServerHttpRequest result = new TrustedProxyForwardedHeaderTransformer(List.of("10.0.0.0/8")).apply(request);

        assertThat(result.getURI().toString()).isEqualTo("https://farm.example.test/x");   // prefix bị bỏ qua
    }

    /** Bộ giới hạn đăng nhập theo IP đọc remoteAddress: phải là IP khách khi đi qua edge, IP TCP khi gọi thẳng. */
    @Test
    void clientIpComesFromTheEdgeOnlyWhenTrusted() {
        var transformer = new TrustedProxyForwardedHeaderTransformer(List.of("10.0.0.0/8"));

        ServerHttpRequest viaEdge = transformer.apply(MockServerHttpRequest.get("http://gw.internal/x")
                .remoteAddress(new InetSocketAddress("10.1.2.3", 4000))
                .header("X-Forwarded-For", "198.51.100.7").build());
        assertThat(viaEdge.getRemoteAddress().getHostString()).isEqualTo("198.51.100.7");

        ServerHttpRequest direct = transformer.apply(MockServerHttpRequest.get("http://gw.internal/x")
                .remoteAddress(new InetSocketAddress("203.0.113.9", 4000))
                .header("X-Forwarded-For", "198.51.100.7").build());
        assertThat(direct.getRemoteAddress().getHostString()).isEqualTo("203.0.113.9");
    }
}
