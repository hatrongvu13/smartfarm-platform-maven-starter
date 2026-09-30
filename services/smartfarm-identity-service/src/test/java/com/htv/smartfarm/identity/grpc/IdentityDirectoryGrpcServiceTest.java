package com.htv.smartfarm.identity.grpc;

import com.htv.smartfarm.identity.account.application.PrincipalProfileService;
import com.htv.smartfarm.identity.authorization.application.AuthorizationService;
import com.htv.smartfarm.identity.authorization.application.model.PermissionDecision;
import com.htv.smartfarm.identity.authorization.application.model.PermissionTarget;
import com.htv.smartfarm.identity.grpc.security.GrpcCaller;
import com.htv.smartfarm.identity.grpc.security.GrpcRequestSecurity;
import com.htv.smartfarm.identity.grpc.security.GrpcRequestSecurity.SecuredRequest;
import com.htv.smartfarm.proto.common.v1.PingRequest;
import com.htv.smartfarm.proto.common.v1.PingResponse;
import com.htv.smartfarm.proto.common.v1.RequestContext;
import com.htv.smartfarm.proto.identity.v1.*;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityDirectoryGrpcServiceTest {

    private static final String TENANT_ID = "tenant-001";
    private static final String SUBJECT_ID = "user-001";
    private static final String FARM_ID = "farm-001";

    @Mock
    private PrincipalProfileService principalProfileService;

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private IdentityProtoMapper protoMapper;

    @Mock
    private GrpcRequestSecurity requestSecurity;

    @Mock
    private GrpcCaller grpcCaller;

    private IdentityDirectoryGrpcService grpcService;

    @BeforeEach
    void setUp() {
        grpcService = new IdentityDirectoryGrpcService(
                principalProfileService,
                authorizationService,
                protoMapper,
                requestSecurity,
                new GrpcExceptionMapper(),
                "1.0.0-test"
        );
    }

    @Test
    void getPrincipal_shouldReturnMappedPrincipal() {
        RequestContext context = requestContext();

        GetPrincipalRequest request =
                GetPrincipalRequest.newBuilder()
                        .setContext(context)
                        .setSubjectId(SUBJECT_ID)
                        .build();

        SecuredRequest securedRequest =
                securedRequest();

        var principalData =
                org.mockito.Mockito.mock(
                        com.htv.smartfarm.identity.account
                                .application.model.PrincipalData.class
                );

        Principal principalProto =
                Principal.newBuilder()
                        .setSubjectId(SUBJECT_ID)
                        .setActive(true)
                        .build();

        when(requestSecurity.authorizePrincipalRead(
                context,
                SUBJECT_ID
        )).thenReturn(securedRequest);

        when(principalProfileService.getPrincipal(
                TENANT_ID,
                SUBJECT_ID
        )).thenReturn(principalData);

        when(protoMapper.toProto(principalData))
                .thenReturn(principalProto);

        TestStreamObserver<GetPrincipalResponse> observer =
                new TestStreamObserver<>();

        grpcService.getPrincipal(
                request,
                observer
        );

        assertThat(observer.error()).isNull();
        assertThat(observer.completed()).isTrue();
        assertThat(observer.value()).isNotNull();

        assertThat(
                observer.value()
                        .getPrincipal()
                        .getSubjectId()
        ).isEqualTo(SUBJECT_ID);

        verify(principalProfileService).getPrincipal(
                TENANT_ID,
                SUBJECT_ID
        );
    }

    @Test
    void checkPermission_shouldReturnMappedDecision() {
        RequestContext context = requestContext();

        com.htv.smartfarm.proto.identity.v1.PermissionTarget
                protoTarget =
                com.htv.smartfarm.proto.identity.v1
                        .PermissionTarget
                        .newBuilder()
                        .setResourceType("FARM")
                        .setResourceId(FARM_ID)
                        .setAction("READ")
                        .build();

        CheckPermissionRequest request =
                CheckPermissionRequest.newBuilder()
                        .setContext(context)
                        .setSubjectId(SUBJECT_ID)
                        .setTarget(protoTarget)
                        .build();

        PermissionTarget applicationTarget =
                new PermissionTarget(
                        "FARM",
                        FARM_ID,
                        "READ"
                );

        PermissionDecision decision =
                PermissionDecision.allow(
                        applicationTarget,
                        "ALLOWED_BY_ROLE"
                );

        CheckPermissionResponse expectedResponse =
                CheckPermissionResponse.newBuilder()
                        .setAllowed(true)
                        .setReasonCode("ALLOWED_BY_ROLE")
                        .build();

        when(requestSecurity.authorizePermissionCheck(
                context,
                SUBJECT_ID
        )).thenReturn(securedRequest());

        when(protoMapper.toApplicationTarget(protoTarget))
                .thenReturn(applicationTarget);

        when(authorizationService.checkPermission(
                TENANT_ID,
                SUBJECT_ID,
                applicationTarget
        )).thenReturn(decision);

        when(protoMapper.toProto(decision))
                .thenReturn(expectedResponse);

        TestStreamObserver<CheckPermissionResponse> observer =
                new TestStreamObserver<>();

        grpcService.checkPermission(
                request,
                observer
        );

        assertThat(observer.error()).isNull();
        assertThat(observer.completed()).isTrue();
        assertThat(observer.value().getAllowed()).isTrue();

        assertThat(observer.value().getReasonCode())
                .isEqualTo("ALLOWED_BY_ROLE");
    }

    @Test
    void batchCheckPermissions_shouldReturnAllMappedDecisions() {
        RequestContext context = requestContext();

        com.htv.smartfarm.proto.identity.v1.PermissionTarget
                readTargetProto =
                protoTarget("READ");

        com.htv.smartfarm.proto.identity.v1.PermissionTarget
                updateTargetProto =
                protoTarget("UPDATE");

        BatchCheckPermissionsRequest request =
                BatchCheckPermissionsRequest.newBuilder()
                        .setContext(context)
                        .setSubjectId(SUBJECT_ID)
                        .addTargets(readTargetProto)
                        .addTargets(updateTargetProto)
                        .build();

        PermissionTarget readTarget =
                new PermissionTarget(
                        "FARM",
                        FARM_ID,
                        "READ"
                );

        PermissionTarget updateTarget =
                new PermissionTarget(
                        "FARM",
                        FARM_ID,
                        "UPDATE"
                );

        PermissionDecision allowedDecision =
                PermissionDecision.allow(
                        readTarget,
                        "ALLOWED_BY_ROLE"
                );

        PermissionDecision deniedDecision =
                PermissionDecision.deny(
                        updateTarget,
                        "PERMISSION_NOT_GRANTED"
                );

        var allowedProtoDecision =
                com.htv.smartfarm.proto.identity.v1
                        .PermissionDecision
                        .newBuilder()
                        .setTarget(readTargetProto)
                        .setAllowed(true)
                        .setReasonCode("ALLOWED_BY_ROLE")
                        .build();

        var deniedProtoDecision =
                com.htv.smartfarm.proto.identity.v1
                        .PermissionDecision
                        .newBuilder()
                        .setTarget(updateTargetProto)
                        .setAllowed(false)
                        .setReasonCode(
                                "PERMISSION_NOT_GRANTED"
                        )
                        .build();

        when(requestSecurity.authorizePermissionCheck(
                context,
                SUBJECT_ID
        )).thenReturn(securedRequest());

        when(protoMapper.toApplicationTargets(
                request.getTargetsList()
        )).thenReturn(List.of(
                readTarget,
                updateTarget
        ));

        when(authorizationService.batchCheckPermissions(
                TENANT_ID,
                SUBJECT_ID,
                List.of(readTarget, updateTarget)
        )).thenReturn(List.of(
                allowedDecision,
                deniedDecision
        ));

        when(protoMapper.toPermissionDecisionProto(
                allowedDecision
        )).thenReturn(allowedProtoDecision);

        when(protoMapper.toPermissionDecisionProto(
                deniedDecision
        )).thenReturn(deniedProtoDecision);

        TestStreamObserver<BatchCheckPermissionsResponse>
                observer = new TestStreamObserver<>();

        grpcService.batchCheckPermissions(
                request,
                observer
        );

        assertThat(observer.error()).isNull();
        assertThat(observer.completed()).isTrue();
        assertThat(observer.value().getDecisionsCount())
                .isEqualTo(2);

        assertThat(
                observer.value()
                        .getDecisions(0)
                        .getAllowed()
        ).isTrue();

        assertThat(
                observer.value()
                        .getDecisions(1)
                        .getReasonCode()
        ).isEqualTo("PERMISSION_NOT_GRANTED");
    }

    @Test
    void ping_shouldReturnServiceInformation() {
        when(requestSecurity.authenticatedCaller())
                .thenReturn(grpcCaller);

        PingRequest request =
                PingRequest.newBuilder()
                        .setContext(requestContext())
                        .build();

        TestStreamObserver<PingResponse> observer =
                new TestStreamObserver<>();

        grpcService.ping(
                request,
                observer
        );

        assertThat(observer.error()).isNull();
        assertThat(observer.completed()).isTrue();

        assertThat(observer.value().getService())
                .isEqualTo("smartfarm-identity-service");

        assertThat(observer.value().getStatus())
                .isEqualTo("UP");

        assertThat(observer.value().getVersion())
                .isEqualTo("1.0.0-test");
    }

    @Test
    void checkPermission_shouldReturnInvalidArgument_whenTargetIsMissing() {
        CheckPermissionRequest request =
                CheckPermissionRequest.newBuilder()
                        .setContext(requestContext())
                        .setSubjectId(SUBJECT_ID)
                        .build();

        TestStreamObserver<CheckPermissionResponse> observer =
                new TestStreamObserver<>();

        grpcService.checkPermission(
                request,
                observer
        );

        assertThat(observer.value()).isNull();
        assertThat(observer.completed()).isFalse();
        assertThat(observer.error()).isNotNull();

        assertThat(
                io.grpc.Status
                        .fromThrowable(observer.error())
                        .getCode()
        ).isEqualTo(io.grpc.Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void batchCheckPermissions_shouldRejectMoreThanOneHundredTargets() {
        BatchCheckPermissionsRequest.Builder request =
                BatchCheckPermissionsRequest.newBuilder()
                        .setContext(requestContext())
                        .setSubjectId(SUBJECT_ID);

        for (int index = 0; index < 101; index++) {
            request.addTargets(
                    com.htv.smartfarm.proto.identity.v1
                            .PermissionTarget
                            .newBuilder()
                            .setResourceType("USER")
                            .setResourceId("user-" + index)
                            .setAction("READ")
                            .build()
            );
        }

        TestStreamObserver<BatchCheckPermissionsResponse>
                observer = new TestStreamObserver<>();

        grpcService.batchCheckPermissions(
                request.build(),
                observer
        );

        assertThat(observer.error()).isNotNull();

        assertThat(
                io.grpc.Status
                        .fromThrowable(observer.error())
                        .getCode()
        ).isEqualTo(io.grpc.Status.Code.INVALID_ARGUMENT);
    }

    private SecuredRequest securedRequest() {
        return new SecuredRequest(
                grpcCaller,
                TENANT_ID,
                SUBJECT_ID
        );
    }

    private RequestContext requestContext() {
        return RequestContext.newBuilder()
                .setTenantId(TENANT_ID)
                .setActorId(SUBJECT_ID)
                .setCorrelationId("corr-001")
                .build();
    }

    private com.htv.smartfarm.proto.identity.v1.PermissionTarget
    protoTarget(String action) {
        return com.htv.smartfarm.proto.identity.v1
                .PermissionTarget
                .newBuilder()
                .setResourceType("FARM")
                .setResourceId(FARM_ID)
                .setAction(action)
                .build();
    }

    private static final class TestStreamObserver<T>
            implements StreamObserver<T> {

        private T value;
        private Throwable error;
        private boolean completed;

        @Override
        public void onNext(T value) {
            this.value = value;
        }

        @Override
        public void onError(Throwable throwable) {
            this.error = throwable;
        }

        @Override
        public void onCompleted() {
            this.completed = true;
        }

        T value() {
            return value;
        }

        Throwable error() {
            return error;
        }

        boolean completed() {
            return completed;
        }
    }
}