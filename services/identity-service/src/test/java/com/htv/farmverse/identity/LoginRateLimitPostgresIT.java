package com.htv.farmverse.identity;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.htv.farmverse.identity.login.JdbcLoginAttemptCounter;
import com.htv.farmverse.identity.login.LoginAttemptCounter;
import com.htv.farmverse.identity.login.LoginRateLimitFilter;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M8: giới hạn đăng nhập theo IP dùng chung giữa nhiều instance identity, trên PostgreSQL thật.
 *
 * <p>"Hai instance" là hai {@link JdbcLoginAttemptCounter} (và hai {@link LoginRateLimitFilter}) độc lập, cùng một
 * database, giống hai pod. Chạy bằng vai trò ứng dụng {@code identity_app} (chỉ DML), bảng do Flyway tạo (V3).
 * Mỗi test dùng khóa riêng để không ảnh hưởng nhau.
 */
@SpringBootTest
@ActiveProfiles("test")
class LoginRateLimitPostgresIT {

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        PostgresTestcontainer.register(registry);
    }

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    LoginAttemptCounter configured;

    static String key() {
        return "login:test-" + UUID.randomUUID();
    }

    @Test
    void theApplicationUsesTheSharedDatabaseCounter() {
        assertThat(configured).isInstanceOf(JdbcLoginAttemptCounter.class);
    }

    @Test
    void twoInstancesShareOneCount() {
        LoginAttemptCounter a = new JdbcLoginAttemptCounter(jdbc);
        LoginAttemptCounter b = new JdbcLoginAttemptCounter(jdbc);
        String key = key();

        List<Integer> counts = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            counts.add((i % 2 == 0 ? a : b).hit(key, Duration.ofMinutes(1)).count());
        }

        assertThat(counts).containsExactly(1, 2, 3, 4, 5, 6);
    }

    /** Hai filter (hai instance), giới hạn 3: lần thứ 4 bị chặn dù rơi vào instance khác. */
    @Test
    void theFourthAttemptIsBlockedEvenOnAnotherInstance() throws Exception {
        LoginRateLimitFilter podA = new LoginRateLimitFilter(3, Duration.ofMinutes(1), new JdbcLoginAttemptCounter(jdbc));
        LoginRateLimitFilter podB = new LoginRateLimitFilter(3, Duration.ofMinutes(1), new JdbcLoginAttemptCounter(jdbc));
        String address = "198.51.100." + (1 + (int) (Math.random() * 250));
        jdbc.update("DELETE FROM login_attempt_window WHERE bucket = ?", "login:" + address);

        assertThat(post(podA, address)).isEqualTo(200);
        assertThat(post(podB, address)).isEqualTo(200);
        assertThat(post(podA, address)).isEqualTo(200);
        assertThat(post(podB, address)).isEqualTo(429);
        assertThat(post(podA, address)).isEqualTo(429);
    }

    /** Nhiều request cùng lúc từ hai instance: không mất lần đếm nào (mỗi số 1..N xuất hiện đúng một lần). */
    @Test
    void concurrentHitsAreNeverLost() throws Exception {
        LoginAttemptCounter a = new JdbcLoginAttemptCounter(jdbc);
        LoginAttemptCounter b = new JdbcLoginAttemptCounter(jdbc);
        String key = key();
        int n = 40;
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> results = IntStream.range(0, n)
                    .mapToObj(i -> pool.submit(() -> {
                        start.await();
                        return (i % 2 == 0 ? a : b).hit(key, Duration.ofMinutes(1)).count();
                    }))
                    .toList();
            start.countDown();
            Set<Integer> seen = ConcurrentHashMap.newKeySet();
            for (Future<Integer> result : results) {
                seen.add(result.get(30, TimeUnit.SECONDS));
            }
            assertThat(seen).isEqualTo(IntStream.rangeClosed(1, n).boxed().collect(Collectors.toSet()));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void windowEndsAndCountStartsAgain() throws Exception {
        LoginAttemptCounter counter = new JdbcLoginAttemptCounter(jdbc);
        String key = key();
        Duration window = Duration.ofSeconds(1);

        LoginAttemptCounter.Attempt first = counter.hit(key, window);
        assertThat(first.count()).isEqualTo(1);
        assertThat(first.retryAfterSeconds()).isEqualTo(1L);
        assertThat(counter.hit(key, window).count()).isEqualTo(2);

        Thread.sleep(1200);

        assertThat(counter.hit(key, window).count()).as("cửa sổ mới").isEqualTo(1);
    }

    @Test
    void expiredWindowsAreDeleted() throws Exception {
        JdbcLoginAttemptCounter counter = new JdbcLoginAttemptCounter(jdbc);
        String expired = key();
        String live = key();
        counter.hit(expired, Duration.ofMillis(100));
        counter.hit(live, Duration.ofMinutes(5));
        Thread.sleep(300);

        counter.deleteExpired();

        assertThat(jdbc.queryForList("SELECT bucket FROM login_attempt_window WHERE bucket IN (?, ?)", String.class, expired, live))
                .containsExactly(live);
    }

    private static int post(LoginRateLimitFilter filter, String address) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
        request.setRemoteAddr(address);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response.getStatus();
    }
}
