package com.htv.smartfarm.security.grpc;

import com.htv.smartfarm.security.TokenVerifier;
import com.htv.smartfarm.security.grpc.autoconfigure.SmartFarmGrpcSecurityAutoConfiguration;
import io.grpc.*;

import java.util.UUID;

/**
 * Fail closed. Do not trust x-tenant-id without comparing it with the verified JWT.
 */
public final class JwtServerInterceptor implements ServerInterceptor {
    private static final Metadata.Key<String> AUTH = Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> CORR = Metadata.Key.of("x-correlation-id", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> TENANT = Metadata.Key.of("x-tenant-id", Metadata.ASCII_STRING_MARSHALLER);
    private final TokenVerifier verifier;
    private final GrpcMethodPolicy policy;

    public JwtServerInterceptor(TokenVerifier verifier, GrpcMethodPolicy policy) {
        this.verifier = verifier;
        this.policy = policy;
    }

    public JwtServerInterceptor(TokenVerifier verifier) {
        this(verifier, GrpcMethodPolicy.authenticatedByDefault());
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        String method = call.getMethodDescriptor().getFullMethodName();
        if (policy.isPublic(method)) return next.startCall(call, headers);
        String bearer = headers.get(AUTH);
        if (bearer == null || !bearer.startsWith("Bearer ") || bearer.length() <= 7)
            return deny(call, Status.UNAUTHENTICATED);
        TokenVerifier.VerifiedToken principal;
        try {
            principal = verifier.verify(bearer.substring(7));
        } catch (RuntimeException invalid) {
            return deny(call, Status.UNAUTHENTICATED);
        }
        if (principal == null || principal.subject() == null || principal.subject().isBlank() || principal.tenantId() == null || principal.tenantId().isBlank() || principal.roles() == null)
            return deny(call, Status.UNAUTHENTICATED);
        String tenantHeader = headers.get(TENANT);
        if (tenantHeader != null && !tenantHeader.equals(principal.tenantId()))
            return deny(call, Status.PERMISSION_DENIED);
        String required = policy.requiredAuthority(method);
        if (required != null && !principal.roles().contains(required)
                && !com.htv.smartfarm.security.jwt.JwtAuthorities.isSuperAdmin(principal.roles()))
            return deny(call, Status.PERMISSION_DENIED);
        String correlation = headers.get(CORR);

        if (correlation == null || correlation.isBlank()) {
            correlation =
                    SmartFarmGrpcSecurityAutoConfiguration
                            .CORRELATION_CONTEXT
                            .get();
        }

        if (correlation == null || correlation.isBlank()) {
            correlation = UUID.randomUUID().toString();
        }
        if (correlation == null || correlation.isBlank() || correlation.length() > 128)
            correlation = UUID.randomUUID().toString();
        Context context = Context.current().withValue(GrpcSecurityContext.SUBJECT, principal.subject())
                .withValue(GrpcSecurityContext.TENANT, principal.tenantId())
                .withValue(GrpcSecurityContext.AUTHORITIES, principal.roles())
                .withValue(GrpcSecurityContext.CORRELATION_ID, correlation);
        return Contexts.interceptCall(context, call, headers, next);
    }

    private static <ReqT, RespT> ServerCall.Listener<ReqT> deny(ServerCall<ReqT, RespT> call, Status status) {
        call.close(status, new Metadata());
        return new ServerCall.Listener<>() {
        };
    }
}
