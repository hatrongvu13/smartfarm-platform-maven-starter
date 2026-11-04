package com.htv.farmverse.gateway;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.util.UriComponentsBuilder;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Gateway với Redis THẬT (Testcontainers), đăng nhập bằng ACL user {@code gateway} đúng như gợi ý trong
 * {@code docs/architecture/deployment.md} — nếu quyền ACL thiếu, test này đỏ.
 *
 * <ul>
 *   <li>Phiên nằm trong Redis với namespace {@code farmverse:gateway:session} và TTL theo {@code GATEWAY_SESSION_TIMEOUT}.</li>
 *   <li>Đăng nhập đổi id phiên ở chính Redis: key của id cũ không còn.</li>
 *   <li>Bộ giới hạn tốc độ (script Lua) chạy được bằng user ACL — nếu không, nó tự cho qua và chỉ ghi log.</li>
 *   <li>Redis treo (container bị pause): request có phiên nhận 503 trong khoảng timeout, không treo theo.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "farmverse.gateway.oidc.client-secret=test-secret",
        "spring.profiles.active=test",
        "GATEWAY_SESSION_TIMEOUT=7m",
        "REDIS_TIMEOUT=1s",
        "REDIS_CONNECT_TIMEOUT=500ms",
        "FARM_URL=http://127.0.0.1:1",
        "farmverse.gateway.login-rate-limit.max-attempts=" + GatewayRedisIT.LIMIT
})
class GatewayRedisIT {

    /** Đủ cho các test đăng nhập khác (mỗi test 2 lượt); test giới hạn tự dọn bộ đếm sau khi chạy. */
    static final int LIMIT = 25;

    static final String ADMIN_PASSWORD = "it-admin-password";
    static final String GATEWAY_PASSWORD = "it-gateway-password";
    static final String NAMESPACE = "farmverse:gateway:session";

    /**
     * Quyền ACL đúng như docs/architecture/deployment.md. {@code +time}: script Lua của RequestRateLimiter gọi
     * {@code TIME}, lệnh này không thuộc các nhóm ở trên (thiếu nó, bộ giới hạn âm thầm cho qua mọi request).
     */
    static final String ACL = "on >" + GATEWAY_PASSWORD
            + " ~farmverse:gateway:* ~request_rate_limiter.*"
            + " +@read +@write +@keyspace +@scripting +@connection +time -@dangerous";

    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:8-alpine")
            .withCommand("redis-server", "--requirepass", ADMIN_PASSWORD)
            .withExposedPorts(6379);
    static final FakeIdentityServer IDENTITY;

    static {
        REDIS.start();
        String[] acl = ("redis-cli -a " + ADMIN_PASSWORD + " --no-auth-warning ACL SETUSER gateway " + ACL).split(" ");
        assertThat(cli(acl)).contains("OK");
        try {
            IDENTITY = new FakeIdentityServer();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void redis(DynamicPropertyRegistry registry) {
        registry.add("REDIS_HOST", REDIS::getHost);
        registry.add("REDIS_PORT", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.username", () -> "gateway");
        registry.add("spring.data.redis.password", () -> GATEWAY_PASSWORD);
        registry.add("farmverse.gateway.oidc.issuer", IDENTITY::issuer);
    }

    @AfterAll
    static void stop() {
        IDENTITY.close();
        REDIS.stop();
    }

    @LocalServerPort
    int port;

    WebTestClient web() {
        return WebTestClient.bindToServer().baseUrl("http://127.0.0.1:" + port)
                .responseTimeout(Duration.ofSeconds(15)).build();
    }

    @Test
    void loginSessionLivesInRedisWithTtlAndTheOldIdIsGone() {
        Login login = login();

        assertThat(keys(NAMESPACE + ":*")).anyMatch(k -> k.endsWith(login.after()));
        assertThat(keys(NAMESPACE + ":*")).as("id phiên trước đăng nhập không còn trong Redis")
                .noneMatch(k -> k.endsWith(login.before()));
        long ttl = Long.parseLong(admin("TTL", NAMESPACE + ":sessions:" + login.after()).trim());
        assertThat(ttl).isBetween(60L, Duration.ofMinutes(7).toSeconds());

        // Đọc lại phiên từ Redis (deserialize) và dùng được
        web().get().uri("/bff/me").cookie("FV_SESSION", login.after()).exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.userId").isEqualTo(FakeIdentityServer.USER_ID);
    }

    @Test
    void rateLimiterScriptRunsUnderTheAclUser() {
        Login login = login();

        // farm-uri là cổng chết → 502 sau khi qua bộ giới hạn; điều cần kiểm là bộ giới hạn đã ghi được key
        web().get().uri("/api/farm/farms").cookie("FV_SESSION", login.after()).exchange()
                .expectStatus().isEqualTo(502);

        assertThat(keys("request_rate_limiter.*")).as("script Lua của RequestRateLimiter phải chạy được bằng user ACL")
                .isNotEmpty();
    }

    @org.springframework.beans.factory.annotation.Autowired
    com.htv.farmverse.gateway.config.LoginAttemptCounter loginAttempts;

    /** G5, phần Redis: script INCR + PEXPIRE chạy bằng user ACL, key có TTL đúng cửa sổ (không bao giờ thiếu TTL). */
    @Test
    void loginCounterScriptRunsUnderTheAclUserAndAlwaysHasATtl() {
        String key = "it-" + System.nanoTime();
        assertThat(loginAttempts).isInstanceOf(com.htv.farmverse.gateway.config.RedisLoginAttemptCounter.class);
        assertThat(loginAttempts.increment(key, Duration.ofSeconds(30)).block()).isEqualTo(1L);
        assertThat(loginAttempts.increment(key, Duration.ofSeconds(30)).block()).isEqualTo(2L);

        long ttl = Long.parseLong(admin("PTTL", "farmverse:gateway:login-limit:" + key).trim());
        assertThat(ttl).isBetween(1L, 30_000L);
    }

    /** G5 qua HTTP với Redis thật: vượt giới hạn thì 429 ProblemDetail. Dọn bộ đếm để không chặn test khác. */
    @Test
    void tooManyLoginStartsFromOneIpGet429() {
        try {
            int status = 0;
            for (int i = 0; i < LIMIT + 1; i++) {
                status = web().get().uri("/oauth2/authorization/farmverse").exchange()
                        .expectBody().returnResult().getStatus().value();
            }
            assertThat(status).isEqualTo(429);
            web().get().uri("/oauth2/authorization/farmverse").exchange()
                    .expectStatus().isEqualTo(429)
                    .expectHeader().exists("Retry-After")
                    .expectCookie().doesNotExist("FV_SESSION")   // chặn trước Security: không tạo phiên trong Redis
                    .expectBody().jsonPath("$.code").isEqualTo("SF-GATEWAY-429");
        } finally {
            keys("farmverse:gateway:login-limit:*").forEach(k -> admin("DEL", k));
        }
    }

    @Test
    void hungRedisFailsClosedWithin503() {
        Login login = login();
        var docker = DockerClientFactory.instance().client();
        docker.pauseContainerCmd(REDIS.getContainerId()).exec();
        try {
            long started = System.nanoTime();
            web().get().uri("/bff/me").cookie("FV_SESSION", login.after()).exchange()
                    .expectStatus().isEqualTo(503)
                    .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                    .expectBody().jsonPath("$.code").isEqualTo("SF-GATEWAY-503");
            assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(5));
        } finally {
            docker.unpauseContainerCmd(REDIS.getContainerId()).exec();
        }
    }

    // ---- hỗ trợ ----

    record Login(String before, String after) {
    }

    /** Đăng nhập trọn vẹn với identity giả; trả id phiên trước và sau đăng nhập. */
    Login login() {
        EntityExchangeResult<byte[]> start = web().get().uri("/oauth2/authorization/farmverse").exchange()
                .expectStatus().is3xxRedirection().expectBody().returnResult();
        ResponseCookie before = start.getResponseCookies().getFirst("FV_SESSION");
        assertThat(before).isNotNull();
        Map<String, String> params = query(start.getResponseHeaders().getLocation());
        IDENTITY.nonce = params.get("nonce");

        EntityExchangeResult<byte[]> callback = web().get()
                .uri("/login/oauth2/code/farmverse?code=it-code&state={state}", params.get("state"))
                .cookie("FV_SESSION", before.getValue())
                .exchange().expectStatus().is3xxRedirection().expectBody().returnResult();
        assertThat(callback.getResponseHeaders().getLocation()).hasPath("/");
        ResponseCookie after = callback.getResponseCookies().getFirst("FV_SESSION");
        assertThat(after).isNotNull();
        return new Login(before.getValue(), after.getValue());
    }

    static Map<String, String> query(URI location) {
        Map<String, String> params = new LinkedHashMap<>();
        UriComponentsBuilder.fromUri(location).build().getQueryParams().toSingleValueMap()
                .forEach((k, v) -> params.put(k, URLDecoder.decode(v, StandardCharsets.UTF_8)));
        return params;
    }

    static List<String> keys(String pattern) {
        String out = admin("--scan", "--pattern", pattern);
        return Arrays.stream(out.split("\n")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    /** redis-cli bằng user quản trị (default) — chỉ test dùng, gateway không có mật khẩu này. */
    static String admin(String... args) {
        String[] command = new String[args.length + 4];
        command[0] = "redis-cli";
        command[1] = "-a";
        command[2] = ADMIN_PASSWORD;
        command[3] = "--no-auth-warning";
        System.arraycopy(args, 0, command, 4, args.length);
        return cli(command);
    }

    static String cli(String... command) {
        try {
            var result = REDIS.execInContainer(command);
            assertThat(result.getExitCode()).as(result.getStderr()).isZero();
            return result.getStdout();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
