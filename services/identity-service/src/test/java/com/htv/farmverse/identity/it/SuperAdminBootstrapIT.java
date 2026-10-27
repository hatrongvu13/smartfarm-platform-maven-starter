package com.htv.farmverse.identity.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.htv.farmverse.identity.account.repository.UserAccountRepository;
import com.htv.farmverse.identity.authorization.repository.MembershipRoleRepository;
import com.htv.farmverse.identity.bootstrap.IdentityBootstrapLockEntity;
import com.htv.farmverse.identity.bootstrap.SuperAdminBootstrapService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

@EnabledIfSystemProperty(named = "it.postgres.enabled", matches = "true")
class SuperAdminBootstrapIT extends AbstractIdentityPostgresIT {

    @Autowired
    private SuperAdminBootstrapService bootstrap;

    @Autowired
    private UserAccountRepository accounts;

    @Autowired
    private MembershipRoleRepository membershipRoles;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void concurrentRequestsCreateExactlyOneSuperAdmin() throws Exception {
        String email = "bootstrap-" + UUID.randomUUID() + "@it.test";
        CyclicBarrier barrier = new CyclicBarrier(2);
        Callable<Object> attempt = () -> {
            barrier.await();
            try {
                return bootstrap.createFirstSuperAdmin(
                        email,
                        "Valid-Identity-Password-123!"
                );
            } catch (ResponseStatusException failure) {
                return failure;
            }
        };

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Object>> futures = executor.invokeAll(
                    List.of(attempt, attempt)
            );
            List<Object> results = List.of(
                    futures.get(0).get(),
                    futures.get(1).get()
            );

            assertThat(results.stream()
                    .filter(SuperAdminBootstrapService.BootstrapResult.class::isInstance)
                    .count()).isEqualTo(1);
            assertThat(results.stream()
                    .filter(ResponseStatusException.class::isInstance)
                    .map(ResponseStatusException.class::cast)
                    .map(ResponseStatusException::getStatusCode))
                    .containsExactly(HttpStatus.CONFLICT);
        } finally {
            executor.shutdownNow();
        }

        assertThat(accounts.existsByNormalizedEmail(email)).isTrue();
        assertThat(membershipRoles.countByRoleCode(
                SuperAdminBootstrapService.SUPERADMIN
        )).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from sf_identity_bootstrap_lock where id = ?",
                Integer.class,
                IdentityBootstrapLockEntity.SUPERADMIN_LOCK_ID
        )).isEqualTo(1);
    }
}
