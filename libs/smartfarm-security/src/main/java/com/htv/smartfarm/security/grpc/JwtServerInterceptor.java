package com.htv.smartfarm.security.grpc;

import com.htv.smartfarm.security.TokenVerifier;
import com.htv.smartfarm.security.jwt.JwtAuthorities;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

import java.util.Set;
import java.util.UUID;

/**
 * Fail closed.
 *
 * <p>Không tin tưởng x-tenant-id nếu chưa đối chiếu với tenant
 * trong JWT đã được xác minh.
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

        String authorization = headers.get(
                AUTHORIZATION_HEADER
        );

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

        TokenVerifier.VerifiedToken principal;

        try {
            principal = verifier.verify(rawToken);
        } catch (RuntimeException exception) {
            return deny(
                    call,
                    Status.UNAUTHENTICATED.withDescription(
                            "Bearer token verification failed"
                    )
            );
        }

        if (!isValidPrincipal(principal)) {
            return deny(
                    call,
                    Status.UNAUTHENTICATED.withDescription(
                            "Verified token is missing required claims"
                    )
            );
        }

        Set<String> authorities = principal.roles();

        String tenantHeader = normalize(
                headers.get(TENANT_HEADER)
        );

        if (tenantHeader != null
                && !tenantHeader.equals(principal.tenantId())) {
            return deny(
                    call,
                    Status.PERMISSION_DENIED.withDescription(
                            "x-tenant-id does not match authenticated tenant"
                    )
            );
        }

        String requiredAuthority =
                policy.requiredAuthority(fullMethodName);

        if (!hasRequiredAuthority(
                authorities,
                requiredAuthority
        )) {
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
                .withValue(
                        GrpcSecurityContext.SUBJECT,
                        principal.subject()
                )
                .withValue(
                        GrpcSecurityContext.TENANT,
                        principal.tenantId()
                )
                .withValue(
                        GrpcSecurityContext.AUTHORITIES,
                        Set.copyOf(authorities)
                )
                .withValue(
                        GrpcSecurityContext.CORRELATION_ID,
                        correlationId
                );

        return Contexts.interceptCall(
                context,
                call,
                headers,
                next
        );
    }

    private boolean hasRequiredAuthority(
            Set<String> authorities,
            String requiredAuthority
    ) {
        if (requiredAuthority == null
                || requiredAuthority.isBlank()) {
            return true;
        }

        return authorities.contains(requiredAuthority)
                || JwtAuthorities.isSuperAdmin(authorities);
    }

    private boolean isValidBearerHeader(String authorization) {
        return authorization != null
                && authorization.startsWith(BEARER_PREFIX)
                && authorization.length() > BEARER_PREFIX.length()
                && !authorization
                .substring(BEARER_PREFIX.length())
                .isBlank();
    }

    private boolean isValidPrincipal(
            TokenVerifier.VerifiedToken principal
    ) {
        return principal != null
                && hasText(principal.subject())
                && hasText(principal.tenantId())
                && principal.roles() != null;
    }

    private String resolveCorrelationId(Metadata headers) {
        /*
         * Ưu tiên giá trị đã được correlation interceptor chuẩn hóa.
         */
        String correlationId = normalize(
                GrpcSecurityContext.CORRELATION_ID.get()
        );

        /*
         * Fallback khi correlation interceptor không được bật hoặc
         * JwtServerInterceptor được sử dụng độc lập.
         */
        if (correlationId == null) {
            correlationId = normalize(
                    headers.get(CORRELATION_HEADER)
            );
        }

        if (!isValidCorrelationId(correlationId)) {
            return UUID.randomUUID().toString();
        }

        return correlationId;
    }

    private boolean isValidCorrelationId(
            String correlationId
    ) {
        return correlationId != null
                && !correlationId.isBlank()
                && correlationId.length()
                <= MAX_CORRELATION_ID_LENGTH;
    }

    private String normalize(String value) {
        return value == null || value.isBlank()
                ? null
                : value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static <ReqT, RespT>
    ServerCall.Listener<ReqT> deny(
            ServerCall<ReqT, RespT> call,
            Status status
    ) {
        call.close(status, new Metadata());

        return new ServerCall.Listener<>() {
        };
    }
}