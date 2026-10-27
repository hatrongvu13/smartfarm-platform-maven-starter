package com.htv.farmverse.identity.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.Set;

import com.htv.farmverse.identity.grpc.security.IdentityGrpcAuthorities;
import com.htv.farmverse.proto.identity.v1.IdentityTokenServiceGrpc;
import com.htv.farmverse.security.TokenVerifier;
import com.htv.farmverse.security.core.SecurityIdentity;
import com.htv.farmverse.security.core.TokenType;
import com.htv.farmverse.security.grpc.GrpcMethodPolicy;
import com.htv.farmverse.security.grpc.JwtServerInterceptor;

import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.Status;
import org.junit.jupiter.api.Test;

class IdentityTokenGrpcSecurityTest {

    private static final Metadata.Key<String> AUTHORIZATION =
            Metadata.Key.of(
                    "authorization",
                    Metadata.ASCII_STRING_MARSHALLER
            );

    private final TokenVerifier verifier = mock(TokenVerifier.class);
    private final GrpcMethodPolicy policy = new IdentityGrpcSecurityConfiguration()
            .identityGrpcMethodPolicy();
    private final JwtServerInterceptor interceptor =
            new JwtServerInterceptor(verifier, policy);

    @Test
    void machineIssueIsPublicAtJwtLayerForCredentialBootstrap() {
        var call = call(IdentityTokenServiceGrpc.getIssueServiceTokenMethod());
        var handler = handler(call);

        interceptor.interceptCall(call, new Metadata(), handler);

        verify(handler).startCall(any(), any(Metadata.class));
        verify(verifier, never()).verify(any());
    }

    @Test
    void delegatedExchangeRejectsMissingBearerToken() {
        var call = call(
                IdentityTokenServiceGrpc.getExchangeDelegatedTokenMethod()
        );

        interceptor.interceptCall(call, new Metadata(), handler(call));

        verifyClosedWith(call, Status.Code.UNAUTHENTICATED);
    }

    @Test
    void sessionReadRequiresItsDeclaredAuthority() {
        var call = call(IdentityTokenServiceGrpc.getListSessionsMethod());
        var handler = handler(call);
        when(verifier.verify("valid-user-token")).thenReturn(identity(Set.of()));

        interceptor.interceptCall(
                call,
                bearerMetadata("valid-user-token"),
                handler
        );

        verifyClosedWith(call, Status.Code.PERMISSION_DENIED);
        verify(handler, never()).startCall(any(), any(Metadata.class));
    }

    @Test
    void sessionReadAllowsTheDeclaredAuthority() {
        var call = call(IdentityTokenServiceGrpc.getListSessionsMethod());
        var handler = handler(call);
        when(verifier.verify("valid-user-token")).thenReturn(identity(Set.of(
                IdentityGrpcAuthorities.SESSION_READ
        )));

        interceptor.interceptCall(
                call,
                bearerMetadata("valid-user-token"),
                handler
        );

        verify(handler).startCall(any(), any(Metadata.class));
    }

    private static SecurityIdentity identity(Set<String> authorities) {
        return new SecurityIdentity(
                "user-1",
                null,
                "tenant-1",
                authorities,
                Set.of(),
                TokenType.USER
        );
    }

    private static Metadata bearerMetadata(String token) {
        Metadata metadata = new Metadata();
        metadata.put(AUTHORIZATION, "Bearer " + token);
        return metadata;
    }

    private static <ReqT, RespT> ServerCall<ReqT, RespT> call(
            MethodDescriptor<ReqT, RespT> method
    ) {
        ServerCall<ReqT, RespT> call = mock(ServerCall.class);
        when(call.getMethodDescriptor()).thenReturn(method);
        return call;
    }

    private static <ReqT, RespT> ServerCallHandler<ReqT, RespT> handler(
            ServerCall<ReqT, RespT> call
    ) {
        return mock(ServerCallHandler.class);
    }

    private static void verifyClosedWith(
            ServerCall<?, ?> call,
            Status.Code code
    ) {
        org.mockito.ArgumentCaptor<Status> status =
                org.mockito.ArgumentCaptor.forClass(Status.class);
        verify(call).close(status.capture(), any(Metadata.class));
        assertThat(status.getValue().getCode()).isEqualTo(code);
    }
}
