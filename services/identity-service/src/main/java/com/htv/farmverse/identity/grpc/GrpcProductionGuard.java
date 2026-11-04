package com.htv.farmverse.identity.grpc;

import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * T7: ở prod, server gRPC phải chạy TLS với bundle và bắt buộc cert client, reflection tắt. Thiếu một điều kiện thì
 * identity không khởi động, thay vì lặng lẽ mở danh bạ thành viên ra mạng nội bộ không mã hóa.
 */
final class GrpcProductionGuard {

    private final Environment environment;

    GrpcProductionGuard(Environment environment) {
        this.environment = environment;
    }

    void check() {
        if (!environment.getProperty("spring.grpc.server.enabled", Boolean.class, true)) {
            return;
        }
        String bundle = environment.getProperty("spring.grpc.server.ssl.bundle");
        boolean sslEnabled = environment.getProperty("spring.grpc.server.ssl.enabled", Boolean.class, StringUtils.hasText(bundle));
        String clientAuth = environment.getProperty("spring.grpc.server.ssl.client-auth", "none");
        boolean reflection = environment.getProperty("spring.grpc.server.reflection.enabled", Boolean.class, true);
        if (!StringUtils.hasText(bundle) || !sslEnabled) {
            throw new IllegalStateException("prod: spring.grpc.server.ssl.bundle bắt buộc (gRPC không chạy plaintext)");
        }
        if (!"require".equalsIgnoreCase(clientAuth)) {
            throw new IllegalStateException("prod: spring.grpc.server.ssl.client-auth phải là REQUIRE (mTLS)");
        }
        if (reflection) {
            throw new IllegalStateException("prod: spring.grpc.server.reflection.enabled phải là false");
        }
    }
}
