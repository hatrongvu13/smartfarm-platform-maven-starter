package com.htv.smartfarm.identity.tenant.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.htv.smartfarm.identity.IdentityApplication;
import com.htv.smartfarm.identity.account.domain.UserAccountEntity;
import com.htv.smartfarm.identity.account.domain.UserProfileEntity;
import com.htv.smartfarm.identity.authorization.application.PageSupport;
import com.htv.smartfarm.identity.authorization.domain.MembershipRoleEntity;
import com.htv.smartfarm.identity.authorization.domain.RoleEntity;
import com.htv.smartfarm.identity.tenant.domain.MembershipStatus;
import com.htv.smartfarm.identity.tenant.domain.TenantEntity;
import com.htv.smartfarm.identity.tenant.domain.TenantMembershipEntity;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(
        classes = IdentityApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.grpc.server.port=0",
                "smartfarm.identity.mqtt.enabled=false",
                "smartfarm.identity.identity-audience=smartfarm-identity",
                "smartfarm.identity.private-key-path=classpath:.local/identity-private.pem",
                "smartfarm.identity.public-key-path=classpath:.local/identity-public.pem",
                "smartfarm.identity.issuer=http://localhost:8092",
                "smartfarm.security.jwt.issuer=http://localhost:8092",
                "smartfarm.security.jwt.jwk-set-uri=http://localhost:8092/.well-known/jwks.json",
                "smartfarm.security.jwt.audiences=smartfarm-identity",
                "smartfarm.security.jwt.allow-local-http=true"
        }
)
@ActiveProfiles("test")
class TenantMembershipSearchRepositoryTest {

    @Autowired
    private TenantMembershipRepository memberships;

    @Autowired
    private Environment environment;

    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    void executesPagedTenantUserSearchInTheDatabase() {
        assertThat(environment.getProperty("spring.jpa.open-in-view", Boolean.class))
                .isFalse();

        var tenant = new TenantEntity("tenant-list", "tenant-list", "Tenant");
        var account = new UserAccountEntity("subject-list", "seed@example.test", "password-hash");
        var membership = new TenantMembershipEntity("membership-list", tenant, account);
        var role = new RoleEntity("role-list", tenant, "ADMIN", "Admin", true);
        entityManager.persist(tenant);
        entityManager.persist(account);
        entityManager.persist(new UserProfileEntity(account, "Seed User", "en", "UTC"));
        entityManager.persist(membership);
        entityManager.persist(role);
        entityManager.persist(new MembershipRoleEntity(membership, role, "test-actor"));
        entityManager.flush();

        var result = memberships.searchTenantUsers(
                tenant.getId(),
                MembershipStatus.INVITED,
                "seed",
                "ADMIN",
                PageSupport.pageable(2, "")
        );

        assertThat(result.getContent())
                .extracting(TenantMembershipEntity::getId)
                .containsExactly("membership-list");
        assertThat(result.hasNext()).isFalse();
    }
}
