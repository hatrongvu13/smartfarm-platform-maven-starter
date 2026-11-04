package com.htv.farmverse.gateway;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Actuator chỉ ở cổng quản trị riêng (giống identity và farm), không bao giờ ở cổng công khai.
 *
 * <p>Cổng quản trị đặt qua biến {@code GATEWAY_MANAGEMENT_PORT} (không đặt thẳng {@code management.server.port}),
 * nên test kiểm luôn việc {@code application.yml} nối biến đó vào cổng quản trị.
 * Gọi như người lạ: không phiên, không cert. Không cần Redis.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "farmverse.gateway.oidc.client-secret=test-secret",
        "spring.data.redis.password=test",
        "spring.profiles.active=test"
})
@Import(InMemorySessionTestConfiguration.class)
class GatewayActuatorExposureTest {

    /**
     * Cổng quản trị CỐ ĐỊNH (lấy một cổng đang trống), không dùng 0. Lý do: Spring Cloud Gateway bỏ qua route
     * trên cổng quản trị bằng cách so cổng thật của request với SỐ trong {@code management.server.port}
     * ({@code RoutePredicateHandlerMapping#getHandlerInternal}). Với 0 thì không khớp, route {@code web} chạy cả
     * ở cổng quản trị — chỉ xảy ra trong test, không xảy ra với 9080 thật.
     */
    @DynamicPropertySource
    static void fixedManagementPort(DynamicPropertyRegistry registry) throws IOException {
        int port;
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            port = socket.getLocalPort();
        }
        registry.add("GATEWAY_MANAGEMENT_PORT", () -> port);
    }

    @LocalServerPort
    int publicPort;

    @LocalManagementPort
    int managementPort;

    private static WebTestClient client(int port) {
        return WebTestClient.bindToServer().baseUrl("http://127.0.0.1:" + port).build();
    }

    @Test
    void managementRunsOnItsOwnPort() {
        assertThat(managementPort).isPositive().isNotEqualTo(publicPort);
    }

    /**
     * Trên cổng công khai, {@code /actuator/**} không còn là actuator: request rơi vào route {@code web}
     * (SPA). Không khẳng định mã trạng thái vì nó phụ thuộc dev server của web có chạy hay không;
     * chỉ khẳng định không có dữ liệu actuator.
     */
    @Test
    void publicPortDoesNotServeActuator() {
        for (String path : new String[] {"/actuator", "/actuator/info", "/actuator/health"}) {
            client(publicPort).get().uri(path).exchange()
                    .expectBody(String.class).value(body -> assertThat(body == null ? "" : body)
                            .as(path)
                            .doesNotContain("_links")
                            .doesNotContain("\"status\":\"UP\"")
                            .doesNotContain("\"status\":\"DOWN\""));
        }
    }

    /** Probe dùng liveness/readiness: không phụ thuộc Redis, nên Redis chập chờn không làm orchestrator giết pod. */
    @Test
    void managementPortServesProbes() {
        client(managementPort).get().uri("/actuator/health/liveness").exchange().expectStatus().isOk();
        client(managementPort).get().uri("/actuator/health/readiness").exchange().expectStatus().isOk();
    }

    /** Endpoint {@code gateway} liệt kê route và URI nội bộ: không bật, kể cả ở cổng quản trị. */
    @Test
    void gatewayEndpointIsNotExposed() {
        client(managementPort).get().uri("/actuator/gateway/routes").exchange().expectStatus().isNotFound();
    }
}
