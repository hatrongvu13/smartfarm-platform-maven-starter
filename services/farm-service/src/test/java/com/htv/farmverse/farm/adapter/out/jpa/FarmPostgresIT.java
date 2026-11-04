package com.htv.farmverse.farm.adapter.out.jpa;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import com.htv.farmverse.farm.domain.Farm;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F3 + M4: farm trên PostgreSQL thật, chạy bằng vai trò ứng dụng {@code farm_app} — migration Flyway khớp entity ({@code validate}), cô lập tenant, và hai tranh chấp
 * đồng thời mà {@code FarmExceptionHandler} đổi thành 409. Test đơn vị chỉ giả lập exception; ở đây PostgreSQL
 * thật sự ném chúng.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaFarmRepository.class, TenantFromTokenResolver.class, JpaConfiguration.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)  // mỗi thao tác tự mở transaction của nó
@TestPropertySource(properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate"})
class FarmPostgresIT {

    /** PostgreSQL dựng bằng CHÍNH infra/postgres-init.sh: ứng dụng = farm_app (chỉ DML), Flyway = farm_owner. */
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withCopyFileToContainer(
                    MountableFile.forHostPath(Path.of("../../infra/postgres-init.sh").toAbsolutePath().normalize(), 0755),
                    "/docker-entrypoint-initdb.d/01-farmverse.sh");

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        if (!POSTGRES.isRunning()) {
            POSTGRES.start();
        }
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://" + POSTGRES.getHost() + ":"
                + POSTGRES.getFirstMappedPort() + "/farmverse_farm");
        registry.add("spring.datasource.username", () -> "farm_app");
        registry.add("spring.datasource.password", () -> "farm-app-dev");
        registry.add("spring.flyway.user", () -> "farm_owner");
        registry.add("spring.flyway.password", () -> "farm-owner-dev");
    }

    @Autowired JpaFarmRepository farms;
    @Autowired PlatformTransactionManager tx;

    private final ExecutorService pool = Executors.newFixedThreadPool(2);

    @AfterEach
    void clear() {
        pool.shutdownNow();
        SecurityContextHolder.clearContext();
    }

    @Test
    void tenantsAreIsolatedOnPostgres() {
        String code = "iso-" + System.nanoTime();
        Farm saved = as("tenant-a", () -> farms.save(Farm.create(code, "A", null)));

        assertThat(as("tenant-b", () -> farms.findById(saved.id()))).isEmpty();
        assertThat(as("tenant-b", () -> farms.existsByCode(code))).isFalse();
        // UNIQUE (tenant_id, code): cùng mã ở tenant khác là hợp lệ
        assertThat(as("tenant-b", () -> farms.save(Farm.create(code, "B", null))).code()).isEqualTo(code);
    }

    /** Hai người cùng sửa một farm, transaction chồng lên nhau: {@code @Version} chặn người chậm hơn (→ 409 SF-FARM-010). */
    @Test
    void concurrentUpdatesLoseOnTheVersionCheck() throws Exception {
        Farm farm = as("tenant-a", () -> farms.save(Farm.create("ver-" + System.nanoTime(), "V0", null)));
        CountDownLatch bothLoaded = new CountDownLatch(2);
        CountDownLatch firstCommitted = new CountDownLatch(1);

        Future<?> fast = pool.submit(inTenant("tenant-a", () -> {
            Farm f = farms.findById(farm.id()).orElseThrow();
            bothLoaded.countDown();
            await(bothLoaded);
            f.rename("nhanh", null);
            return farms.save(f);
        }, firstCommitted));
        Future<?> slow = pool.submit(inTenant("tenant-a", () -> {
            Farm f = farms.findById(farm.id()).orElseThrow();
            bothLoaded.countDown();
            await(firstCommitted);          // đọc trước, ghi sau khi người kia đã commit
            f.rename("chậm", null);
            return farms.save(f);
        }, null));

        fast.get(20, TimeUnit.SECONDS);
        assertThat(rootCause(slow)).isInstanceOf(ObjectOptimisticLockingFailureException.class);
        assertThat(as("tenant-a", () -> farms.findById(farm.id())).orElseThrow().name()).isEqualTo("nhanh");
    }

    /** Hai request cùng tạo một mã: cả hai qua bước existsByCode, PostgreSQL chặn bản thứ hai (→ 409 SF-FARM-011). */
    @Test
    void concurrentCreatesOfTheSameCodeHitTheUniqueConstraint() throws Exception {
        String code = "dup-" + System.nanoTime();
        CountDownLatch bothChecked = new CountDownLatch(2);
        List<Future<?>> attempts = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            String name = "Farm " + i;
            attempts.add(pool.submit(inTenant("tenant-a", () -> {
                boolean exists = farms.existsByCode(code);
                bothChecked.countDown();
                await(bothChecked);
                assertThat(exists).as("kiểm tra trong code không thấy trùng").isFalse();
                return farms.save(Farm.create(code, name, null));
            }, null)));
        }

        List<Throwable> failures = new ArrayList<>();
        for (Future<?> attempt : attempts) {
            Throwable failure = rootCause(attempt);
            if (failure != null) {
                failures.add(failure);
            }
        }
        assertThat(failures).hasSize(1).first().isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---- hỗ trợ ----

    private <T> T as(String tenant, Supplier<T> work) {
        authenticate(tenant);
        return new TransactionTemplate(tx).execute(status -> work.get());
    }

    /** Chạy trong một transaction riêng ở luồng khác; {@code afterCommit} được thả sau khi commit xong. */
    private Callable<Object> inTenant(String tenant, Supplier<Object> work, CountDownLatch afterCommit) {
        return () -> {
            try {
                authenticate(tenant);
                return new TransactionTemplate(tx).execute(status -> work.get());
            } finally {
                if (afterCommit != null) {
                    afterCommit.countDown();
                }
                SecurityContextHolder.clearContext();
            }
        };
    }

    private static void authenticate(String tenant) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject("u").claim("tenant_id", tenant)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(15, TimeUnit.SECONDS)).as("luồng kia không tới kịp").isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /** null nếu thành công; ngược lại exception gốc mà Spring ném ra (bỏ lớp bọc của Future). */
    private static Throwable rootCause(Future<?> future) throws InterruptedException {
        try {
            future.get(20, TimeUnit.SECONDS);
            return null;
        } catch (java.util.concurrent.ExecutionException e) {
            return e.getCause();
        } catch (java.util.concurrent.TimeoutException e) {
            throw new AssertionError("thao tác bị treo", e);
        }
    }
}
