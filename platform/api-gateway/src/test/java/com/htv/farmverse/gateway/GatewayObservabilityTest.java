package com.htv.farmverse.gateway;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M7: trace đi xuyên gateway, metric ở cổng quản trị, log không chứa bí mật.
 *
 * <p>Route {@code web} (không cần đăng nhập) trỏ tới một server ghi lại header nhận được, đóng vai service phía sau.
 * Export trace bị Boot tắt trong test; việc tạo span và lan {@code traceparent} vẫn chạy như thật.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "farmverse.gateway.oidc.client-secret=test-secret",
        "spring.data.redis.password=test",
        "spring.profiles.active=test",
        // Mức DEBUG của security và gateway: nếu có chỗ in header/cookie/token thì test bắt được
        "logging.level.org.springframework.security=DEBUG",
        "logging.level.org.springframework.cloud.gateway=DEBUG",
        "logging.level.org.springframework.web=DEBUG"
})
@AutoConfigureMetrics
@ExtendWith(OutputCaptureExtension.class)
@Import(InMemorySessionTestConfiguration.class)
class GatewayObservabilityTest {

    static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    static final String CLIENT_SPAN_ID = "00f067aa0ba902b7";
    static final Pattern TRACEPARENT = Pattern.compile("^00-([0-9a-f]{32})-([0-9a-f]{16})-[0-9a-f]{2}$");

    static final List<Headers> received = new CopyOnWriteArrayList<>();
    static final HttpServer DOWNSTREAM = downstream();

    static HttpServer downstream() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/", ex -> {
                received.add(ex.getRequestHeaders());
                byte[] body = "<!doctype html><title>ok</title>".getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().set("Content-Type", "text/html");
                ex.sendResponseHeaders(200, body.length);
                try (OutputStream out = ex.getResponseBody()) {
                    out.write(body);
                }
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Cổng quản trị cố định, không dùng 0 (lý do ở {@link GatewayActuatorExposureTest}). */
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws IOException {
        int port;
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            port = socket.getLocalPort();
        }
        registry.add("GATEWAY_MANAGEMENT_PORT", () -> port);
        registry.add("WEB_URL", () -> "http://127.0.0.1:" + DOWNSTREAM.getAddress().getPort());
    }

    @AfterAll
    static void stop() {
        DOWNSTREAM.stop(0);
    }

    @BeforeEach
    void reset() {
        received.clear();
    }

    @LocalServerPort
    int publicPort;

    @LocalManagementPort
    int managementPort;

    private static WebTestClient client(int port) {
        return WebTestClient.bindToServer().baseUrl("http://127.0.0.1:" + port).build();
    }

    private static Matcher downstreamTraceparent() {
        assertThat(received).hasSize(1);
        String header = received.get(0).getFirst("traceparent");
        assertThat(header).as("traceparent gửi tới service phía sau").isNotNull();
        Matcher m = TRACEPARENT.matcher(header);
        assertThat(m.matches()).as(header).isTrue();
        return m;
    }

    /** Client (hoặc edge) đã có trace: gateway nối tiếp cùng trace id, span của gateway là cha mới. */
    @Test
    void incomingTraceIsContinuedDownstream(CapturedOutput output) {
        client(publicPort).get().uri("/")
                .header("traceparent", "00-" + TRACE_ID + "-" + CLIENT_SPAN_ID + "-01")
                .exchange().expectStatus().isOk();

        Matcher m = downstreamTraceparent();
        assertThat(m.group(1)).isEqualTo(TRACE_ID);
        assertThat(m.group(2)).as("span của gateway, không phải span của client").isNotEqualTo(CLIENT_SPAN_ID);
        // Log của gateway mang trace id (spring.reactor.context-propagation=auto đưa trace vào MDC)
        assertThat(output.getAll()).contains(TRACE_ID);
    }

    /** Không có trace đến: gateway mở trace mới và vẫn gửi traceparent hợp lệ. */
    @Test
    void newTraceIsStartedWhenNoneArrives() {
        client(publicPort).get().uri("/").exchange().expectStatus().isOk();

        assertThat(downstreamTraceparent().group(1)).isNotEqualTo("0".repeat(32));
    }

    @Test
    void prometheusIsOnTheManagementPortOnly() {
        client(publicPort).get().uri("/").exchange().expectStatus().isOk();

        client(managementPort).get().uri("/actuator/prometheus").exchange()
                .expectStatus().isOk()
                .expectBody(String.class).value(body -> assertThat(body)
                        .contains("jvm_memory_used_bytes")
                        .contains("application=\"api-gateway\"")
                        .contains("http_server_requests_seconds_count"));

        received.clear();
        client(publicPort).get().uri("/actuator/prometheus").exchange()
                .expectBody(String.class).value(body -> assertThat(body == null ? "" : body).doesNotContain("jvm_"));
    }

    /** T16: kể cả ở mức DEBUG, cookie phiên, token, mã OAuth2 và CSRF không xuất hiện trong log. */
    @Test
    void secretsDoNotReachTheLogs(CapturedOutput output) {
        String cookie = "session-secret-6f1d";
        String bearer = "bearer-secret-91ac";
        String csrf = "csrf-secret-2b7e";
        String code = "oauth-code-secret-c40d";

        client(publicPort).get().uri("/")
                .header("Cookie", "FV_SESSION=" + cookie)
                .header("Authorization", "Bearer " + bearer)
                .header("X-XSRF-TOKEN", csrf)
                .exchange().expectStatus().isOk();
        client(publicPort).get().uri("/login/oauth2/code/farmverse?code=" + code + "&state=s1")
                .header("Cookie", "FV_SESSION=" + cookie)
                .exchange();
        client(publicPort).get().uri("/api/farm/farms")
                .header("Authorization", "Bearer " + bearer)
                .exchange().expectStatus().isUnauthorized();

        // Phía sau không nhận cookie của trình duyệt (RemoveRequestHeader=Cookie)
        assertThat(received.get(0).containsKey("Cookie")).as("Cookie gửi tới service phía sau").isFalse();
        assertThat(output.getAll())
                .contains(" DEBUG ")                 // DEBUG đang bật thật, test không vô nghĩa
                .doesNotContain(cookie)
                .doesNotContain(bearer)
                .doesNotContain(csrf)
                .doesNotContain(code);
    }
}
