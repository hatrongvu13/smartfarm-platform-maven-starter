package com.htv.farmverse.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.session.WebSessionIdResolver;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Thuộc tính cookie phiên {@code FV_SESSION} theo cờ {@code farmverse.gateway.secure-cookie}
 * (prod bật trong {@code application-prod.yml}). Luồng HTTP thật nằm ở {@code LoginSessionSecurityTest}.
 */
class SessionCookieAttributesTest {

    private static ResponseCookie issue(boolean secure) {
        WebSessionIdResolver resolver = new SecurityConfiguration().webSessionIdResolver(secure);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/"));
        resolver.setSessionId(exchange, "opaque-id");
        ResponseCookie cookie = exchange.getResponse().getCookies().getFirst("FV_SESSION");
        assertThat(cookie).isNotNull();
        return cookie;
    }

    @Test
    void prodCookieIsSecureHttpOnlyLaxRootPath() {
        ResponseCookie cookie = issue(true);
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
        assertThat(cookie.getPath()).isEqualTo("/");
        assertThat(cookie.getDomain()).as("không đặt Domain: cookie chỉ gửi về đúng host gateway").isNull();
    }

    /** Cờ phải thật sự bật ở profile prod; xóa nhầm dòng đó là cookie phiên đi qua HTTP thường. */
    @Test
    void prodProfileEnablesSecureCookie() {
        var yaml = new org.springframework.beans.factory.config.YamlPropertiesFactoryBean();
        yaml.setResources(new org.springframework.core.io.ClassPathResource("application-prod.yml"));
        assertThat(yaml.getObject()).containsEntry("farmverse.gateway.secure-cookie", true);
    }

    @Test
    void devCookieIsNotSecureSoItWorksOnPlainHttpLocalhost() {
        ResponseCookie cookie = issue(false);
        assertThat(cookie.isSecure()).isFalse();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
    }
}
