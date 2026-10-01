package com.htv.smartfarm.security.grpc;

import java.util.UUID;

import com.htv.smartfarm.security.TokenVerifier;
import com.htv.smartfarm.security.core.SecurityIdentity;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

/**
 * Fail-closed JWT authentication and exact-method authorization interceptor.
 */
public final class JwtServerInterceptor implements ServerInterceptor {

    private static final Metadata.Key<String> AUTHORIZATION_HEADER =
            Metadata.Key.of(
                    "authorization",
                    Metadata.ASCII_STRING_MARSHALLER
            );

    private static final Metadata.Key<String> CORRELATION_HEADER =
            Metadata.Key.of(
                    "x-correlation-id",
                    Metadata.ASCII_STRING_MARSHALLER
            );

    private static final Metadata.Key<String> TENANT_HEADER =
            Metadata.Key.of(
                    "x-tenant-id",
                    Metadata.ASCII_STRING_MARSHALLER
            );

    private static final String BEARER_PREFIX = "Bearer ";
    private static final int MAX_CORRELATION_ID_LENGTH = 128;

    private final TokenVerifier verifier;
    private final GrpcMethodPolicy policy;

    public JwtServerInterceptor(
            TokenVerifier verifier,
            GrpcMethodPolicy policy
    ) {
        this.verifier = verifier;
        this.policy = policy;
    }

    public JwtServerInterceptor(TokenVerifier verifier) {
        this(
                verifier,
                GrpcMethodPolicy.authenticatedByDefault()
        );
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next
    ) {
        String fullMethodName = call
                .getMethodDescriptor()
                .getFullMethodName();

        if (policy.isPublic(fullMethodName)) {
            return next.startCall(call, headers);
        }

        String authorization = headers.get(AUTHORIZATION_HEADER);

        if (!isValidBearerHeader(authorization)) {
            return deny(
                    call,
                    Status.UNAUTHENTICATED.withDescription(
                            "Missing or invalid bearer token"
                    )
            );
        }

        String rawToken = authorization
                .substring(BEARER_PREFIX.length())
                .trim();

        SecurityIdentity identity;

        try {
            identity = verifier.verify(rawToken);
        } catch (RuntimeException exception) {
            return deny(
                    call,
                    Status.UNAUTHENTICATED.withDescription(
                            "Bearer token verification failed"
                    )
            );
        }

        if (identity == null) {
            return deny(
                    call,
                    Status.UNAUTHENTICATED.withDescription(
                            "Verified identity is missing"
                    )
            );
        }

        String tenantHeader = normalize(headers.get(TENANT_HEADER));

        if (tenantHeader != null
                && !tenantHeader.equals(identity.tenantId())) {
            return deny(
                    call,
                    Status.PERMISSION_DENIED.withDescription(
                            "x-tenant-id does not match authenticated tenant"
                    )
            );
        }

        String requiredAuthority =
                policy.requiredAuthority(fullMethodName);

        if (!hasRequiredAuthority(identity, requiredAuthority)) {
            return deny(
                    call,
                    Status.PERMISSION_DENIED.withDescription(
                            "Missing required authority: "
                                    + requiredAuthority
                    )
            );
        }

        String correlationId = resolveCorrelationId(headers);

        Context context = Context.current()
                .withValue(GrpcSecurityContext.IDENTITY, identity)
                .withValue(
                        GrpcSecurityContext.CORRELATION_ID,
                        correlationId
                )
                /* Transitional legacy keys. Remove in phase 3B. */
                .withValue(
                        GrpcSecurityContext.SUBJECT,
                        identity.subject()
                )
                .withValue(
                        GrpcSecurityContext.TENANT,
                        identity.tenantId()
                )
                .withValue(
                        GrpcSecurityContext.AUTHORITIES,
                        identity.authorities()
                );

        return Contexts.interceptCall(
                context,
                call,
                headers,
                next
        );
    }

    private boolean hasRequiredAuthority(
            SecurityIdentity identity,
            String requiredAuthority
    ) {
        if (requiredAuthority == null
                || requiredAuthority.isBlank()) {
            return true;
        }

        return identity.hasAuthority(requiredAuthority)
                || identity.isSuperAdmin();
    }

    private boolean isValidBearerHeader(String authorization) {
        return authorization != null
                && authorization.startsWith(BEARER_PREFIX)
                && authorization.length() > BEARER_PREFIX.length()
                && !authorization
                .substring(BEARER_PREFIX.length())
                .isBlank();
    }

    private String resolveCorrelationId(Metadata headers) {
        String correlationId = normalize(
                GrpcSecurityContext.CORRELATION_ID.get()
        );

        if (correlationId == null) {
            correlationId = normalize(
                    headers.get(CORRELATION_HEADER)
            );
        }

        if (correlationId == null
                || correlationId.length()
                > MAX_CORRELATION_ID_LENGTH) {
            return UUID.randomUUID().toString();
        }

        return correlationId;
    }

    private String normalize(String value) {
        return value == null || value.isBlank()
                ? null
                : value.trim();
    }

    private static <ReqT, RespT> ServerCall.Listener<ReqT> deny(
            ServerCall<ReqT, RespT> call,
            Status status
    ) {
        call.close(status, new Metadata());
        return new ServerCall.Listener<>() {
        };
    }
}
