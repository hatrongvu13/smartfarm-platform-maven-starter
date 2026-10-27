package com.htv.farmverse.security.grpc.dynamic;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class GrpcAuthorizationModeTest {
    @Test
    void exposesOffShadowAndEnforceModes() {
        assertThat(GrpcAuthorizationMode.values()).contains(
                GrpcAuthorizationMode.OFF,
                GrpcAuthorizationMode.SHADOW,
                GrpcAuthorizationMode.ENFORCE
        );
    }
}
