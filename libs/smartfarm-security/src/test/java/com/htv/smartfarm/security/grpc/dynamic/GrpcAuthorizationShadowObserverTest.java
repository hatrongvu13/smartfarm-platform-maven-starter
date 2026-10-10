package com.htv.smartfarm.security.grpc.dynamic;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import com.htv.smartfarm.security.core.SecurityIdentity;
import com.htv.smartfarm.security.core.TokenType;
import org.junit.jupiter.api.Test;

class GrpcAuthorizationShadowObserverTest {
    @Test void countsDivergenceWithoutChangingDecision() {
        var observer = new GrpcAuthorizationShadowObserver();
        var identity = new SecurityIdentity(
                "svc:gateway",
                "gateway",
                "tenant-1",
                Set.of("SCOPE_identity:user:read"),
                Set.of("smartfarm-identity"),
                TokenType.SERVICE
        );

        observer.observe(
                "service/method",
                identity,
                true,
                GrpcAuthorizationDecision.deny("PERMISSION_NOT_GRANTED")
        );

        assertThat(observer.count("static_allow_dynamic_deny"))
                .isEqualTo(1L);
    }
}
