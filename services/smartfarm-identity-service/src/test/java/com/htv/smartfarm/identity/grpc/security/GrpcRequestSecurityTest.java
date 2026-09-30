package com.htv.smartfarm.identity.grpc.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Set;

import com.htv.smartfarm.proto.common.v1.RequestContext;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GrpcRequestSecurityTest {

    private static final String SUBJECT_ID = "user-001";
    private static final String OTHER_SUBJECT_ID = "user-002";
    private static final String TENANT_ID = "tenant-001";
    private static final String OTHER_TENANT_ID = "tenant-002";
    private static final String CORRELATION_ID = "corr-001";

    @Mock
    private GrpcCallerProvider callerProvider;

    private GrpcRequestSecurity requestSecurity;

    @BeforeEach
    void setUp() {
        requestSecurity = new GrpcRequestSecurity(
                callerProvider
        );
    }

    @Test
    void authorizePrincipalRead_shouldAllowOwnPrincipal() {
        mockCaller(Set.of(
                GrpcRequestSecurity.PRINCIPAL_READ
        ));

        RequestContext context = requestContext(
                TENANT_ID,
                SUBJECT_ID,
                CORRELATION_ID
        );

        var securedRequest =
                requestSecurity.authorizePrincipalRead(
                        context,
                        SUBJECT_ID
                );

        assertThat(securedRequest.tenantId())
                .isEqualTo(TENANT_ID);

        assertThat(securedRequest.subjectId())
                .isEqualTo(SUBJECT_ID);

        assertThat(securedRequest.caller().subjectId())
                .isEqualTo(SUBJECT_ID);
    }

    @Test
    void authorizePrincipalRead_shouldUseAuthenticatedSubject_whenRequestIsEmpty() {
        mockCaller(Set.of(
                GrpcRequestSecurity.PRINCIPAL_READ
        ));

        RequestContext context = requestContext(
                TENANT_ID,
                SUBJECT_ID,
                CORRELATION_ID
        );

        var securedRequest =
                requestSecurity.authorizePrincipalRead(
                        context,
                        ""
                );

        assertThat(securedRequest.subjectId())
                .isEqualTo(SUBJECT_ID);
    }

    @Test
    void authorizePrincipalRead_shouldRejectMissingAuthority() {
        mockCaller(Set.of());

        RequestContext context = requestContext(
                TENANT_ID,
                SUBJECT_ID,
                CORRELATION_ID
        );

        assertGrpcStatus(
                () -> requestSecurity.authorizePrincipalRead(
                        context,
                        SUBJECT_ID
                ),
                Status.Code.PERMISSION_DENIED,
                "Missing required authority"
        );
    }

    @Test
    void authorizePrincipalRead_shouldRejectAnotherPrincipal() {
        mockCaller(Set.of(
                GrpcRequestSecurity.PRINCIPAL_READ
        ));

        RequestContext context = requestContext(
                TENANT_ID,
                SUBJECT_ID,
                CORRELATION_ID
        );

        assertGrpcStatus(
                () -> requestSecurity.authorizePrincipalRead(
                        context,
                        OTHER_SUBJECT_ID
                ),
                Status.Code.PERMISSION_DENIED,
                "another principal"
        );
    }

    @Test
    void authorizePrincipalRead_shouldAllowAnotherPrincipal_withImpersonateAuthority() {
        mockCaller(Set.of(
                GrpcRequestSecurity.PRINCIPAL_READ,
                GrpcRequestSecurity.PRINCIPAL_IMPERSONATE
        ));

        RequestContext context = requestContext(
                TENANT_ID,
                SUBJECT_ID,
                CORRELATION_ID
        );

        var securedRequest =
                requestSecurity.authorizePrincipalRead(
                        context,
                        OTHER_SUBJECT_ID
                );

        assertThat(securedRequest.subjectId())
                .isEqualTo(OTHER_SUBJECT_ID);
    }

    @Test
    void authorizeProfileUpdate_shouldRejectTenantMismatch() {
        mockCaller(Set.of(
                GrpcRequestSecurity.PRINCIPAL_UPDATE
        ));

        RequestContext context = requestContext(
                OTHER_TENANT_ID,
                SUBJECT_ID,
                CORRELATION_ID
        );

        assertGrpcStatus(
                () -> requestSecurity.authorizeProfileUpdate(
                        context,
                        SUBJECT_ID
                ),
                Status.Code.PERMISSION_DENIED,
                "tenant"
        );
    }

    @Test
    void authorizeProfileUpdate_shouldAllowCrossTenant_withAuthority() {
        mockCaller(Set.of(
                GrpcRequestSecurity.PRINCIPAL_UPDATE,
                GrpcRequestSecurity.CROSS_TENANT
        ));

        RequestContext context = requestContext(
                OTHER_TENANT_ID,
                SUBJECT_ID,
                CORRELATION_ID
        );

        var securedRequest =
                requestSecurity.authorizeProfileUpdate(
                        context,
                        SUBJECT_ID
                );

        assertThat(securedRequest.tenantId())
                .isEqualTo(OTHER_TENANT_ID);
    }

    @Test
    void authorizePermissionCheck_shouldRejectCorrelationMismatch() {
        mockCaller(Set.of(
                GrpcRequestSecurity.PERMISSION_CHECK
        ));

        RequestContext context = requestContext(
                TENANT_ID,
                SUBJECT_ID,
                "different-correlation-id"
        );

        assertGrpcStatus(
                () -> requestSecurity.authorizePermissionCheck(
                        context,
                        SUBJECT_ID
                ),
                Status.Code.INVALID_ARGUMENT,
                "correlation_id"
        );
    }

    @Test
    void authorizePermissionCheck_shouldRejectActorMismatch() {
        mockCaller(Set.of(
                GrpcRequestSecurity.PERMISSION_CHECK
        ));

        RequestContext context = requestContext(
                TENANT_ID,
                OTHER_SUBJECT_ID,
                CORRELATION_ID
        );

        assertGrpcStatus(
                () -> requestSecurity.authorizePermissionCheck(
                        context,
                        SUBJECT_ID
                ),
                Status.Code.PERMISSION_DENIED,
                "actor_id"
        );
    }

    @Test
    void authorizePermissionCheck_shouldAcceptMissingTenantInBody() {
        mockCaller(Set.of(
                GrpcRequestSecurity.PERMISSION_CHECK
        ));

        RequestContext context = RequestContext.newBuilder()
                .setActorId(SUBJECT_ID)
                .setCorrelationId(CORRELATION_ID)
                .build();

        var securedRequest =
                requestSecurity.authorizePermissionCheck(
                        context,
                        SUBJECT_ID
                );

        assertThat(securedRequest.tenantId())
                .isEqualTo(TENANT_ID);
    }

    @Test
    void superAdmin_shouldBypassSpecificAuthorityChecks() {
        mockCaller(Set.of("SCOPE_*"));

        RequestContext context = requestContext(
                TENANT_ID,
                SUBJECT_ID,
                CORRELATION_ID
        );

        var securedRequest =
                requestSecurity.authorizePrincipalRead(
                        context,
                        SUBJECT_ID
                );

        assertThat(securedRequest.subjectId())
                .isEqualTo(SUBJECT_ID);
    }

    private void mockCaller(Set<String> authorities) {
        when(callerProvider.currentCaller())
                .thenReturn(new GrpcCaller(
                        SUBJECT_ID,
                        TENANT_ID,
                        authorities,
                        CORRELATION_ID
                ));
    }

    private RequestContext requestContext(
            String tenantId,
            String actorId,
            String correlationId
    ) {
        return RequestContext.newBuilder()
                .setTenantId(tenantId)
                .setActorId(actorId)
                .setCorrelationId(correlationId)
                .build();
    }

    private void assertGrpcStatus(
            Runnable action,
            Status.Code expectedCode,
            String expectedDescription
    ) {
        assertThatThrownBy(action::run)
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(exception -> {
                    StatusRuntimeException grpcException =
                            (StatusRuntimeException) exception;

                    assertThat(
                            grpcException.getStatus().getCode()
                    ).isEqualTo(expectedCode);

                    assertThat(
                            grpcException
                                    .getStatus()
                                    .getDescription()
                    ).contains(expectedDescription);
                });
    }
}