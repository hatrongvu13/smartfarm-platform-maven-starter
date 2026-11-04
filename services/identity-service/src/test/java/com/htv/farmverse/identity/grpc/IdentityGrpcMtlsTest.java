package com.htv.farmverse.identity.grpc;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import com.htv.farmverse.contracts.identity.v1.GetMemberRequest;
import com.htv.farmverse.identity.config.IdentityProperties;
import com.htv.farmverse.identity.tenant.TenantAdministrationService;
import com.htv.farmverse.identity.tenant.UserAdministrationService;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthGrpc;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyChannelBuilder;
import io.netty.handler.ssl.SslContextBuilder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.grpc.test.autoconfigure.LocalGrpcServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P2, T2: server gRPC với profile {@code mtls}, cert thật của {@code scripts/generate-dev-mtls.sh}.
 * Ba lớp: TLS đòi cert (không cert, plaintext → đứt); SAN phải là service được phép (gateway, identity → từ chối);
 * rồi mới tới token người dùng (farm không gửi token → UNAUTHENTICATED).
 */
@SpringBootTest(properties = {"farmverse.identity.issuer=https://localhost:8092", "management.server.port=0"})
@ActiveProfiles({"test", "mtls"})
class IdentityGrpcMtlsTest {

    static Path certs;

    @DynamicPropertySource
    static void certificates(DynamicPropertyRegistry registry) throws Exception {
        if (certs == null) {
            certs = Files.createTempDirectory("farmverse-grpc-mtls");
            Path script = Path.of("../../scripts/generate-dev-mtls.sh").toAbsolutePath().normalize();
            Process process = new ProcessBuilder("bash", script.toString(), certs.toString(), "--force")
                    .redirectErrorStream(true).start();
            assertThat(process.waitFor()).as(new String(process.getInputStream().readAllBytes())).isZero();
        }
        registry.add("farmverse.mtls.dir", () -> certs.toString());
    }

    @Autowired TenantAdministrationService tenants;
    @Autowired UserAdministrationService users;
    @Autowired JwtEncoder encoder;
    @Autowired IdentityProperties properties;

    @LocalGrpcServerPort
    int port;

    String tenantId;
    String userId;

    @BeforeEach
    void member() {
        String code = "grpc-mtls-" + System.nanoTime();
        tenantId = tenants.create(code, "mTLS farm").getId();
        userId = users.add(tenantId, code + "@test.local", "Chị Hoa", "Password@12345", Set.of()).userId();
    }

    ManagedChannel tls(String service) throws Exception {
        SslContextBuilder ssl = GrpcSslContexts.forClient().trustManager(certs.resolve("ca.crt").toFile());
        if (service != null) {
            ssl.keyManager(new File(certs.resolve(service + ".crt").toString()), new File(certs.resolve(service + ".key").toString()));
        }
        return NettyChannelBuilder.forAddress("localhost", port).sslContext(ssl.build()).build();
    }

    Status.Code getMember(ManagedChannel channel, boolean withToken) {
        try {
            String token = withToken ? GrpcTokens.user(encoder, properties.issuer(), tenantId, Map.of()) : null;
            GrpcTokens.stub(channel, token).getMember(GetMemberRequest.newBuilder().setTenantId(tenantId).setUserId(userId).build());
            return Status.Code.OK;
        } catch (StatusRuntimeException e) {
            return e.getStatus().getCode();
        } finally {
            channel.shutdownNow();
        }
    }

    @Test
    void farmCertificateWithUserTokenGetsTheMember() throws Exception {
        assertThat(getMember(tls("farm"), true)).isEqualTo(Status.Code.OK);
    }

    @Test
    void otherServicesArePermissionDeniedEvenWithAValidToken() throws Exception {
        assertThat(getMember(tls("gateway"), true)).isEqualTo(Status.Code.PERMISSION_DENIED);
        assertThat(getMember(tls("identity"), true)).isEqualTo(Status.Code.PERMISSION_DENIED);
    }

    @Test
    void noClientCertificateOrPlaintextCannotConnect() throws Exception {
        assertThat(getMember(tls(null), true)).isEqualTo(Status.Code.UNAVAILABLE);
        assertThat(getMember(ManagedChannelBuilder.forAddress("localhost", port).usePlaintext().build(), true))
                .isEqualTo(Status.Code.UNAVAILABLE);
    }

    @Test
    void farmWithoutUserTokenIsUnauthenticated() throws Exception {
        assertThat(getMember(tls("farm"), false)).isEqualTo(Status.Code.UNAUTHENTICATED);
    }

    /** Health không là rpc được bảo vệ: service nào có cert hợp lệ cũng probe được. */
    @Test
    void healthWorksForAnyServiceCertificate() throws Exception {
        ManagedChannel channel = tls("gateway");
        try {
            assertThat(HealthGrpc.newBlockingStub(channel).check(HealthCheckRequest.getDefaultInstance()).getStatusValue()).isEqualTo(1);
        } finally {
            channel.shutdownNow();
        }
    }
}
