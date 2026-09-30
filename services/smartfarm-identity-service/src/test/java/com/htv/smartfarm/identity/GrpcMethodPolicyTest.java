package com.htv.smartfarm.security.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class GrpcMethodPolicyTest {

    private static final String GET_PRINCIPAL =
            "smartfarm.identity.v1.IdentityDirectoryService/GetPrincipal";

    private static final String PING =
            "smartfarm.identity.v1.IdentityDirectoryService/Ping";

    @Test
    void authenticatedByDefault_shouldExposeHealthMethodsOnly() {
        GrpcMethodPolicy policy =
                GrpcMethodPolicy.authenticatedByDefault();

        assertThat(policy.isPublic(
                "grpc.health.v1.Health/Check"
        )).isTrue();

        assertThat(policy.isPublic(
                "grpc.health.v1.Health/Watch"
        )).isTrue();

        assertThat(policy.isPublic(GET_PRINCIPAL))
                .isFalse();

        assertThat(policy.requiredAuthority(GET_PRINCIPAL))
                .isNull();
    }

    @Test
    void builder_shouldRequireAuthorityForMethod() {
        GrpcMethodPolicy policy =
                GrpcMethodPolicy.builder()
                        .requireAuthority(
                                GET_PRINCIPAL,
                                "SCOPE_identity.principal.read"
                        )
                        .build();

        assertThat(policy.isPublic(GET_PRINCIPAL))
                .isFalse();

        assertThat(
                policy.requiredAuthority(GET_PRINCIPAL)
        ).isEqualTo(
                "SCOPE_identity.principal.read"
        );
    }

    @Test
    void builder_shouldConfigureAuthenticatedMethod() {
        GrpcMethodPolicy policy =
                GrpcMethodPolicy.builder()
                        .requireAuthenticated(PING)
                        .build();

        assertThat(policy.isPublic(PING)).isFalse();
        assertThat(policy.requiredAuthority(PING)).isNull();
    }

    @Test
    void builder_shouldConfigurePublicMethod() {
        GrpcMethodPolicy policy =
                GrpcMethodPolicy.builder()
                        .permitAll(PING)
                        .build();

        assertThat(policy.isPublic(PING)).isTrue();
        assertThat(policy.requiredAuthority(PING)).isNull();
    }

    @Test
    void builder_shouldRejectBlankMethodName() {
        assertThatThrownBy(() ->
                GrpcMethodPolicy.builder()
                        .requireAuthenticated(" ")
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("fullMethodName must not be blank");
    }

    @Test
    void builder_shouldRejectBlankAuthority() {
        assertThatThrownBy(() ->
                GrpcMethodPolicy.builder()
                        .requireAuthority(
                                GET_PRINCIPAL,
                                " "
                        )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("authority must not be blank");
    }
}