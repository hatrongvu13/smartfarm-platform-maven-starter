package com.htv.farmverse.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockOidcLogin;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;

/**
 * Không cần identity hay Redis đang chạy: client OIDC khai tĩnh (không discovery),
 * session nằm trong bộ nhớ ({@link InMemorySessionTestConfiguration}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "farmverse.gateway.oidc.client-secret=test-secret",
        "spring.data.redis.password=test",
        "spring.profiles.active=test"
})
@AutoConfigureWebTestClient
@Import(InMemorySessionTestConfiguration.class)
class GatewaySecurityTest {

    /** Bảo đảm test thật sự dùng kho trong bộ nhớ; nếu Boot đổi điều kiện back-off, test này báo ngay. */
    @Test
    void sessionStoreInTestsIsInMemory(@Autowired org.springframework.session.ReactiveSessionRepository<?> sessions) {
        assertThat(sessions).isInstanceOf(org.springframework.session.ReactiveMapSessionRepository.class);
    }

    @Autowired
    WebTestClient web;

    @Test
    void apiWithoutSessionIsUnauthorized() {
        web.get().uri("/api/farm/farms").exchange().expectStatus().isUnauthorized();
        web.get().uri("/bff/me").exchange().expectStatus().isUnauthorized();
    }

    @Autowired
    ReactiveClientRegistrationRepository registrations;
    @Autowired
    org.springframework.context.ApplicationContext context;

    /**
     * Đăng xuất phải đưa TRÌNH DUYỆT tới identity (/connect/logout kèm id_token_hint) để xóa phiên SSO của identity.
     * Nếu bước này không xảy ra, bấm đăng nhập lại sẽ vào thẳng mà không hỏi mật khẩu.
     */
    @Test
    void logoutRedirectsTheBrowserToIdentityEndSession() {
        var registration = registrations.findByRegistrationId("farmverse").block();

        // mockOidcLogin cần client gắn vào context (không đi qua mạng thật)
        WebTestClient mock = WebTestClient.bindToApplicationContext(context).apply(springSecurity())
                .configureClient().build();
        mock.mutateWith(mockOidcLogin().clientRegistration(registration))
                .mutateWith(csrf())
                .post().uri("/logout").exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().value("Location", location -> assertThat(location)
                        .startsWith("http://localhost:8092/connect/logout?id_token_hint=")
                        .contains("post_logout_redirect_uri="));
    }

    /** Web gửi form HTML thật: token CSRF nằm ở cookie XSRF-TOKEN và field _csrf (không có header). */
    @Test
    void logoutFormWithCsrfFieldIsAccepted_andWithoutItIsRejected() {
        var registration = registrations.findByRegistrationId("farmverse").block();
        WebTestClient mock = WebTestClient.bindToApplicationContext(context).apply(springSecurity())
                .configureClient().build();

        mock.mutateWith(mockOidcLogin().clientRegistration(registration))
                .post().uri("/logout")
                .cookie("XSRF-TOKEN", "abc123")
                .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue("_csrf=abc123")
                .exchange()
                .expectStatus().is3xxRedirection();

        mock.mutateWith(mockOidcLogin().clientRegistration(registration))
                .post().uri("/logout")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Autowired
    org.springframework.cloud.gateway.route.RouteLocator routes;

    @Test
    void routesAreDeclared() {
        var ids = routes.getRoutes().map(org.springframework.cloud.gateway.route.Route::getId).collectList().block();
        org.assertj.core.api.Assertions.assertThat(ids).containsExactly("identity-api", "farm-api", "web");
    }
}
