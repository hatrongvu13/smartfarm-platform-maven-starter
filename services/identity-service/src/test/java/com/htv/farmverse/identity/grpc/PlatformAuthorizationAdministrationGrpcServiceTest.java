package com.htv.farmverse.identity.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.concurrent.atomic.AtomicReference;

import com.htv.farmverse.identity.authorization.application.GlobalPermissionCatalogAdministrationService;
import com.htv.farmverse.identity.authorization.application.PlatformAuthorizationAdministrationService;
import com.htv.farmverse.identity.grpc.security.GrpcRequestSecurity;
import com.htv.farmverse.proto.common.v1.RequestContext;
import com.htv.farmverse.proto.identity.v1.GrantPlatformPermissionRequest;
import com.htv.farmverse.proto.identity.v1.GrantPlatformPermissionResponse;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

import org.junit.jupiter.api.Test;

class PlatformAuthorizationAdministrationGrpcServiceTest {

    @Test
    void rejectsWildcardGrantThroughPlatformRoleRpc() {
        var administrationService = mock(PlatformAuthorizationAdministrationService.class);
        var globalPermissions = mock(GlobalPermissionCatalogAdministrationService.class);
        var requestSecurity = mock(GrpcRequestSecurity.class);
        var service = new PlatformAuthorizationAdministrationGrpcService(
                administrationService,
                globalPermissions,
                mock(IdentityAdministrationProtoMapper.class),
                requestSecurity,
                new GrpcExceptionMapper()
        );
        var error = new AtomicReference<Throwable>();
        StreamObserver<GrantPlatformPermissionResponse> observer = new StreamObserver<>() {
            @Override
            public void onNext(GrantPlatformPermissionResponse value) {
                throw new AssertionError("Wildcard grant unexpectedly succeeded");
            }

            @Override
            public void onError(Throwable throwable) {
                error.set(throwable);
            }

            @Override
            public void onCompleted() {
                throw new AssertionError("Wildcard grant unexpectedly completed");
            }
        };

        service.grantPlatformPermission(
                GrantPlatformPermissionRequest.newBuilder()
                        .setContext(RequestContext.newBuilder())
                        .setRoleCode("ADMIN")
                        .setPermissionCode("*")
                        .build(),
                observer
        );

        assertThat(error.get()).isInstanceOf(StatusRuntimeException.class);
        assertThat(((StatusRuntimeException) error.get()).getStatus().getCode())
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
        verifyNoInteractions(administrationService, requestSecurity);
    }
}
