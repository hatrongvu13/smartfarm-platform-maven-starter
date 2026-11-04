package com.htv.farmverse.identity.grpc;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.htv.farmverse.contracts.identity.v1.BatchGetMembersRequest;
import com.htv.farmverse.contracts.identity.v1.GetMemberRequest;
import com.htv.farmverse.contracts.identity.v1.Member;
import com.htv.farmverse.contracts.identity.v1.MemberStatus;
import com.htv.farmverse.identity.config.IdentityProperties;
import com.htv.farmverse.identity.tenant.MembershipStatus;
import com.htv.farmverse.identity.tenant.TenantAdministrationService;
import com.htv.farmverse.identity.tenant.UserAdministrationService;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthGrpc;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.grpc.test.autoconfigure.LocalGrpcServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P2, T1 + D3-B: server gRPC {@code IdentityDirectory} chạy thật (Netty, cổng ngẫu nhiên, plaintext như dev không
 * bật mTLS; mTLS ở {@link IdentityGrpcMtlsTest}). Token ký bằng khóa thật của identity và đi qua cùng
 * {@code JwtDecoder} với REST.
 */
@SpringBootTest
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class IdentityDirectoryGrpcTest {

    @Autowired TenantAdministrationService tenants;
    @Autowired UserAdministrationService users;
    @Autowired JwtEncoder encoder;
    @Autowired IdentityProperties properties;

    @LocalGrpcServerPort
    int port;

    ManagedChannel channel;
    String tenantId;
    String otherTenantId;
    String activeId;
    String suspendedId;
    String outsiderId;

    @BeforeEach
    void data() {
        channel = ManagedChannelBuilder.forAddress("127.0.0.1", port).usePlaintext().build();
        String code = "grpc-" + System.nanoTime();
        tenantId = tenants.create(code, "gRPC farm").getId();
        otherTenantId = tenants.create(code + "-x", "Other farm").getId();
        activeId = users.add(tenantId, code + "-a@test.local", "Chị An", "Password@12345", Set.of()).userId();
        suspendedId = users.add(tenantId, code + "-s@test.local", "Anh Bình", "Password@12345", Set.of()).userId();
        users.changeStatus(tenantId, "someone-else", suspendedId, MembershipStatus.SUSPENDED);
        outsiderId = users.add(otherTenantId, code + "-o@test.local", "Người ngoài", "Password@12345", Set.of()).userId();
    }

    @AfterEach
    void close() {
        channel.shutdownNow();
    }

    String token(Map<String, Object> overrides) {
        return GrpcTokens.user(encoder, properties.issuer(), tenantId, overrides);
    }

    String token() {
        return token(Map.of());
    }

    Member get(String token, String tenant, String userId) {
        return GrpcTokens.stub(channel, token)
                .getMember(GetMemberRequest.newBuilder().setTenantId(tenant).setUserId(userId).build());
    }

    static Status.Code code(Runnable call) {
        try {
            call.run();
            return Status.Code.OK;
        } catch (StatusRuntimeException e) {
            return e.getStatus().getCode();
        }
    }

    @Test
    void activeAndSuspendedMembersOfTheCallersTenant() {
        Member active = get(token(), tenantId, activeId);
        assertThat(active.getUserId()).isEqualTo(activeId);
        assertThat(active.getDisplayName()).isEqualTo("Chị An");
        assertThat(active.getStatus()).isEqualTo(MemberStatus.MEMBER_STATUS_ACTIVE);

        assertThat(get(token(), tenantId, suspendedId).getStatus()).isEqualTo(MemberStatus.MEMBER_STATUS_SUSPENDED);
        // tenant_id trong request có thể bỏ trống: luôn lấy từ token
        assertThat(get(token(), "", activeId).getUserId()).isEqualTo(activeId);
    }

    @Test
    void unknownIdAndMembersOfAnotherTenantAreNotFound() {
        assertThat(code(() -> get(token(), tenantId, UUID.randomUUID().toString()))).isEqualTo(Status.Code.NOT_FOUND);
        assertThat(code(() -> get(token(), tenantId, outsiderId))).isEqualTo(Status.Code.NOT_FOUND);
    }

    /** D3-B: tenant do token quyết định. Hỏi tenant khác bằng tham số request thì bị từ chối. */
    @Test
    void requestTenantMustMatchTheToken() {
        assertThat(code(() -> get(token(), otherTenantId, outsiderId))).isEqualTo(Status.Code.PERMISSION_DENIED);
    }

    @Test
    void missingForgedOrExpiredTokenIsUnauthenticated() {
        String valid = token();
        String forged = valid.substring(0, valid.lastIndexOf('.') + 1) + "AAAA";
        String expired = token(Map.of("exp", java.time.Instant.now().minusSeconds(60), "iat", java.time.Instant.now().minusSeconds(600)));

        assertThat(code(() -> get(null, tenantId, activeId))).isEqualTo(Status.Code.UNAUTHENTICATED);
        assertThat(code(() -> get(forged, tenantId, activeId))).isEqualTo(Status.Code.UNAUTHENTICATED);
        assertThat(code(() -> get(expired, tenantId, activeId))).isEqualTo(Status.Code.UNAUTHENTICATED);
    }

    @Test
    void tokenForAnotherServiceOrWithoutApiScopeOrWithoutUserIsRejected() {
        assertThat(code(() -> get(token(Map.of("aud", java.util.List.of("farm-service"))), tenantId, activeId)))
                .as("aud chỉ có farm-service").isEqualTo(Status.Code.UNAUTHENTICATED);
        assertThat(code(() -> get(token(Map.of("scope", java.util.List.of("openid"))), tenantId, activeId)))
                .as("thiếu scope farmverse.api").isEqualTo(Status.Code.PERMISSION_DENIED);
        assertThat(code(() -> get(token(Map.of("token_type", "service")), tenantId, activeId)))
                .as("token của máy (client_credentials)").isEqualTo(Status.Code.PERMISSION_DENIED);
    }

    @Test
    void invalidIdsAreRejected() {
        assertThat(code(() -> get(token(), tenantId, "not-an-id"))).isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertThat(code(() -> get(token(), tenantId, ""))).isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void batchReturnsKnownMembersOfTheTenantOnlyAndLimitsTheSize() {
        var response = GrpcTokens.stub(channel, token()).batchGetMembers(BatchGetMembersRequest.newBuilder()
                .addUserIds(activeId).addUserIds(outsiderId).addUserIds(UUID.randomUUID().toString())
                .addUserIds(suspendedId).addUserIds(activeId).build());
        assertThat(response.getMembersList()).extracting(Member::getUserId).containsExactly(activeId, suspendedId);

        BatchGetMembersRequest.Builder tooMany = BatchGetMembersRequest.newBuilder();
        for (int i = 0; i < 101; i++) {
            tooMany.addUserIds(UUID.randomUUID().toString());
        }
        assertThat(code(() -> GrpcTokens.stub(channel, token()).batchGetMembers(tooMany.build())))
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    /** Health mở cho probe; rpc không khai trong luật thì bị chặn. */
    @Test
    void healthIsOpenAndPersonalDataIsNotLogged(CapturedOutput output) {
        assertThat(HealthGrpc.newBlockingStub(channel).check(HealthCheckRequest.getDefaultInstance()).getStatusValue())
                .isEqualTo(1);   // SERVING
        get(token(), tenantId, activeId);

        assertThat(output.getAll()).doesNotContain("Chị An").doesNotContain(token());
    }
}
