package com.htv.farmverse.identity.grpc;

import java.util.Set;

import com.htv.farmverse.service.security.FarmVerseClaims;
import com.htv.farmverse.service.security.FarmVerseJwtAuthenticationConverter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.grpc.server.GlobalServerInterceptor;
import org.springframework.grpc.server.security.AuthenticationProcessInterceptor;
import org.springframework.grpc.server.security.GrpcSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Bảo mật của server gRPC (P2), ba lớp như REST:
 * <ol>
 *   <li>mTLS: TLS bắt buộc cert do CA của ta ký (profile {@code mtls}: {@code spring.grpc.server.ssl.client-auth=REQUIRE}).</li>
 *   <li>Service nào: {@link ServiceIdentityInterceptor} đọc SAN {@code spiffe://farmverse/<service>}, chỉ cho
 *       {@code farmverse.grpc.allowed-callers} (mặc định {@code farm}) gọi {@code IdentityDirectory}.</li>
 *   <li>Người dùng nào, tenant nào: access token của người dùng trong metadata {@code authorization}, kiểm bằng cùng
 *       {@link JwtDecoder} với REST (aud {@code identity-service}) và phải có scope {@code farmverse.api}.</li>
 * </ol>
 * Health của gRPC mở cho mọi kết nối đã qua mTLS (probe), reflection chỉ bật ở dev; mọi rpc khác bị từ chối.
 *
 * <p>Tên bean không được là {@code grpcSecurity}: trùng bean của auto-config (đã gặp ở probe P1).
 */
@Configuration(proxyBeanMethods = false)
public class GrpcSecurityConfiguration {

    static final String DIRECTORY = "farmverse.identity.v1.IdentityDirectory/*";

    @Bean
    @GlobalServerInterceptor
    AuthenticationProcessInterceptor identityGrpcAuthorization(GrpcSecurity grpc, JwtDecoder jwtDecoder) throws Exception {
        return grpc
                .oauth2ResourceServer(resource -> resource.jwt(jwt -> jwt
                        .decoder(jwtDecoder)
                        .jwtAuthenticationConverter(new FarmVerseJwtAuthenticationConverter())))
                .authorizeRequests(rpc -> rpc
                        .methods(DIRECTORY).hasAuthority("SCOPE_" + FarmVerseClaims.API_SCOPE)
                        .methods("grpc.health.v1.Health/*").permitAll()
                        .methods("grpc.reflection.v1.ServerReflection/*", "grpc.reflection.v1alpha.ServerReflection/*").permitAll()
                        .allRequests().denyAll())
                .build();
    }

    /** Lớp 2: chỉ có ở profile {@code mtls}, khi kết nối luôn mang cert. Chạy trước lớp kiểm token. */
    @Bean
    @Profile("mtls")
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @GlobalServerInterceptor
    ServiceIdentityInterceptor grpcServiceIdentity(@Value("${farmverse.grpc.allowed-callers:farm}") Set<String> allowed) {
        return new ServiceIdentityInterceptor(allowed, method -> method.startsWith("farmverse.identity.v1.IdentityDirectory/"));
    }

    /** T7: prod không bao giờ chạy gRPC plaintext hay không đòi cert client. */
    @Bean
    @Profile("prod")
    GrpcProductionGuard grpcProductionGuard(Environment environment) {
        GrpcProductionGuard guard = new GrpcProductionGuard(environment);
        guard.check();
        return guard;
    }
}
