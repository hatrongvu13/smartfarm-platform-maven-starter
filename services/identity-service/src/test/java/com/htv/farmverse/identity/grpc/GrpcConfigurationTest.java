package com.htv.farmverse.identity.grpc;

import java.io.IOException;
import java.util.List;

import com.htv.farmverse.contracts.identity.v1.MemberStatus;
import com.htv.farmverse.identity.directory.MemberDirectoryService.MemberInfo;
import com.htv.farmverse.identity.tenant.MembershipStatus;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** T5 (mapper) và T7 (prod không cho gRPC plaintext), không cần context Spring. */
class GrpcConfigurationTest {

    @Test
    void mapperCoversEveryMembershipStatus() {
        for (MembershipStatus status : MembershipStatus.values()) {
            assertThat(MemberMapper.toProto(status)).as(status.name()).isNotEqualTo(MemberStatus.MEMBER_STATUS_UNSPECIFIED);
        }
        assertThat(MemberMapper.toProto((MembershipStatus) null)).isEqualTo(MemberStatus.MEMBER_STATUS_UNSPECIFIED);
        assertThat(MemberMapper.toProto(new MemberInfo("u", null, MembershipStatus.ACTIVE)).getDisplayName()).isEmpty();
    }

    /** Cấu hình thật của prod = application.yml + application-mtls.yml (prod tự bật mtls) + application-prod.yml. */
    @Test
    void shippedProdConfigurationPassesTheGuard() throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        YamlPropertySourceLoader yaml = new YamlPropertySourceLoader();
        for (String file : List.of("application-prod.yml", "application-mtls.yml", "application.yml")) {
            for (PropertySource<?> source : yaml.load(file, new ClassPathResource(file))) {
                environment.getPropertySources().addLast(source);
            }
        }
        assertThat(environment.getProperty("spring.grpc.server.port")).isEqualTo("9093");

        new GrpcProductionGuard(environment).check();
    }

    @Test
    void guardRejectsPlaintextMissingClientAuthAndReflection() {
        MockEnvironment plaintext = new MockEnvironment().withProperty("spring.grpc.server.reflection.enabled", "false");
        assertThatThrownBy(() -> new GrpcProductionGuard(plaintext).check()).hasMessageContaining("ssl.bundle");

        MockEnvironment noClientAuth = new MockEnvironment()
                .withProperty("spring.grpc.server.ssl.bundle", "farmverse")
                .withProperty("spring.grpc.server.reflection.enabled", "false");
        assertThatThrownBy(() -> new GrpcProductionGuard(noClientAuth).check()).hasMessageContaining("client-auth");

        MockEnvironment reflection = new MockEnvironment()
                .withProperty("spring.grpc.server.ssl.bundle", "farmverse")
                .withProperty("spring.grpc.server.ssl.client-auth", "REQUIRE");
        assertThatThrownBy(() -> new GrpcProductionGuard(reflection).check()).hasMessageContaining("reflection");

        MockEnvironment sslDisabled = new MockEnvironment()
                .withProperty("spring.grpc.server.ssl.bundle", "farmverse")
                .withProperty("spring.grpc.server.ssl.enabled", "false")
                .withProperty("spring.grpc.server.ssl.client-auth", "REQUIRE")
                .withProperty("spring.grpc.server.reflection.enabled", "false");
        assertThatThrownBy(() -> new GrpcProductionGuard(sslDisabled).check()).hasMessageContaining("plaintext");
    }
}
