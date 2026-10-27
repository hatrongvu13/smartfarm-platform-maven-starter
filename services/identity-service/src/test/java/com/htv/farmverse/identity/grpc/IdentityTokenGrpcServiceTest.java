package com.htv.farmverse.identity.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.htv.farmverse.identity.grpc.security.GrpcCaller;
import com.htv.farmverse.identity.grpc.security.GrpcCallerProvider;
import com.htv.farmverse.identity.token.ServiceTokenService;
import com.htv.farmverse.identity.token.session.RefreshSessionAdministrationService;
import com.htv.farmverse.proto.common.v1.RequestContext;
import com.htv.farmverse.proto.identity.v1.ExchangeDelegatedTokenRequest;
import com.htv.farmverse.proto.identity.v1.IssueServiceTokenRequest;
import com.htv.farmverse.proto.identity.v1.TokenGrantResponse;
import com.htv.farmverse.security.core.TokenType;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.util.Set;
import org.junit.jupiter.api.Test;

class IdentityTokenGrpcServiceTest {
    private final ServiceTokenService tokenService = mock(ServiceTokenService.class);
    private final GrpcCallerProvider callerProvider = mock(GrpcCallerProvider.class);
    private final IdentityTokenGrpcService service = new IdentityTokenGrpcService(
            tokenService,
            mock(RefreshSessionAdministrationService.class),
            new GrpcExceptionMapper(),
            callerProvider
    );

    @Test
    void issuesMachineTokenUsingRegisteredClientCredentials() {
        when(tokenService.issue(
                "order", "client-secret", "farmverse-inventory", "tenant-1", null
        )).thenReturn(grant("farmverse-inventory", 300));
        var result = new Result<TokenGrantResponse>();

        service.issueServiceToken(
                IssueServiceTokenRequest.newBuilder()
                        .setContext(context("tenant-1", "actor-1"))
                        .setClientId("order")
                        .setClientSecret("client-secret")
                        .setAudience("farmverse-inventory")
                        .build(),
                result
        );

        assertThat(result.error).isNull();
        assertThat(result.value.getAccessToken()).isEqualTo("service-jwt");
        verify(tokenService).issue(
                "order", "client-secret", "farmverse-inventory", "tenant-1", null
        );
    }

    @Test
    void issuesBoundedMachineTokenForLongRunningWorker() {
        when(tokenService.issue(
                "order", "client-secret", "farmverse-inventory", "tenant-1", null
        )).thenReturn(grant("farmverse-inventory", 300));
        var result = new Result<TokenGrantResponse>();

        service.issueServiceToken(
                IssueServiceTokenRequest.newBuilder()
                        .setContext(context("tenant-1", "actor-1"))
                        .setClientId("order")
                        .setClientSecret("client-secret")
                        .setAudience("farmverse-inventory")
                        .build(),
                result
        );

        assertThat(result.error).isNull();
        assertThat(result.value.getAccessToken()).isEqualTo("service-jwt");
        assertThat(result.value.getExpiresInSeconds()).isEqualTo(300);
        verify(tokenService).issue(
                "order", "client-secret", "farmverse-inventory", "tenant-1", null
        );
    }

    @Test
    void rejectsInvalidMachineCredentialsAsUnauthenticated() {
        when(tokenService.issue(
                "order", "wrong-secret", "farmverse-inventory", "tenant-1", null
        )).thenThrow(new SecurityException("credentials are invalid"));
        var result = new Result<TokenGrantResponse>();

        service.issueServiceToken(
                IssueServiceTokenRequest.newBuilder()
                        .setContext(context("tenant-1", ""))
                        .setClientId("order")
                        .setClientSecret("wrong-secret")
                        .setAudience("farmverse-inventory")
                        .build(),
                result
        );

        assertThat(result.value).isNull();
        assertThat(Status.fromThrowable(result.error).getCode())
                .isEqualTo(Status.Code.UNAUTHENTICATED);
        verify(tokenService).issue(
                "order", "wrong-secret", "farmverse-inventory", "tenant-1", null
        );
    }

    @Test
    void delegatedExchangeRequiresActorAndTenantToMatchVerifiedUserJwt() {
        when(callerProvider.currentCaller()).thenReturn(new GrpcCaller(
                "user-1", null, "tenant-1", Set.of(), Set.of(), TokenType.USER, "correlation-1"
        ));
        when(tokenService.issue(
                "gateway", "client-secret", "farmverse-order", "tenant-1", "user-2"
        )).thenReturn(grant("farmverse-order", 300));
        var request = ExchangeDelegatedTokenRequest.newBuilder()
                .setContext(context("tenant-1", "user-1"))
                .setClientId("gateway")
                .setClientSecret("client-secret")
                .setAudience("farmverse-order")
                .setActorSubjectId("user-2")
                .build();
        var result = new Result<TokenGrantResponse>();

        service.exchangeDelegatedToken(request, result);

        assertThat(result.value).isNull();
        assertThat(result.error).isNotNull();
        assertThat(Status.fromThrowable(result.error).getCode())
                .isEqualTo(Status.Code.PERMISSION_DENIED);
        verifyNoInteractions(tokenService);
    }

    @Test
    void delegatedExchangeUsesActorBoundToAuthenticatedUser() {
        when(callerProvider.currentCaller()).thenReturn(new GrpcCaller(
                "user-1", null, "tenant-1", Set.of(), Set.of(), TokenType.USER, "correlation-1"
        ));
        when(tokenService.issue(
                "gateway", "client-secret", "farmverse-order", "tenant-1", "user-1"
        )).thenReturn(grant("farmverse-order", 300));
        var result = new Result<TokenGrantResponse>();

        service.exchangeDelegatedToken(
                ExchangeDelegatedTokenRequest.newBuilder()
                        .setContext(context("tenant-1", "user-1"))
                        .setClientId("gateway")
                        .setClientSecret("client-secret")
                        .setAudience("farmverse-order")
                        .setActorSubjectId("user-1")
                        .build(),
                result
        );

        assertThat(result.error).isNull();
        assertThat(result.value.getAudience()).isEqualTo("farmverse-order");
        verify(tokenService).issue(
                "gateway", "client-secret", "farmverse-order", "tenant-1", "user-1"
        );
    }

    private static RequestContext context(String tenant, String actor) {
        return RequestContext.newBuilder()
                .setTenantId(tenant)
                .setActorId(actor)
                .setCorrelationId("correlation-1")
                .build();
    }

    private static ServiceTokenService.Grant grant(String audience, long expiresIn) {
        return new ServiceTokenService.Grant("service-jwt", "Bearer", expiresIn, audience);
    }

    private static final class Result<T> implements StreamObserver<T> {
        private T value;
        private Throwable error;

        @Override
        public void onNext(T item) {
            value = item;
        }

        @Override
        public void onError(Throwable failure) {
            error = failure;
        }

        @Override
        public void onCompleted() {
        }
    }
}
