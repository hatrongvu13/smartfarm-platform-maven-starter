package com.htv.farmverse.farm.adapter.out.jpa;

import java.time.Instant;

import com.htv.farmverse.farm.domain.Farm;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** Multi-tenant bằng Hibernate {@code @TenantId}: tenant B không thấy, không đọc được farm của tenant A. */
@DataJpaTest
@Import({JpaFarmRepository.class, TenantFromTokenResolver.class, JpaConfiguration.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED) // mỗi tenant một transaction (session gắn tenant lúc mở)
@TestPropertySource(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"})
class TenantIsolationTest {

    @Autowired JpaFarmRepository farms;
    @Autowired PlatformTransactionManager tx;

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void eachTenantSeesOnlyItsOwnFarms() {
        Farm saved = as("tenant-a", () -> farms.save(Farm.create("valley", "Valley A", null)));

        assertThat(as("tenant-a", () -> farms.findAll())).extracting(Farm::code).containsExactly("valley");
        assertThat(as("tenant-b", () -> farms.findAll())).isEmpty();
        assertThat(as("tenant-b", () -> farms.findById(saved.id()))).isEmpty();
        // cùng mã farm vẫn tạo được ở tenant khác
        assertThat(as("tenant-b", () -> farms.existsByCode("valley"))).isFalse();
    }

    private <T> T as(String tenant, java.util.function.Supplier<T> work) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject("u").claim("tenant_id", tenant)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        return new TransactionTemplate(tx).execute(status -> work.get());
    }
}
