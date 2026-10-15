package com.htv.smartfarm.identity.grpc;

import com.htv.smartfarm.identity.token.ServiceTokenService;
import com.htv.smartfarm.identity.token.session.RefreshSessionAdministrationService;
import com.htv.smartfarm.identity.grpc.security.GrpcCaller;
import com.htv.smartfarm.identity.grpc.security.GrpcCallerProvider;
import com.htv.smartfarm.proto.identity.v1.ExchangeDelegatedTokenRequest;
import com.htv.smartfarm.proto.identity.v1.IdentityTokenServiceGrpc;
import com.htv.smartfarm.proto.identity.v1.IssueServiceTokenRequest;
import com.htv.smartfarm.proto.identity.v1.ListSessionsRequest;
import com.htv.smartfarm.proto.identity.v1.ListSessionsResponse;
import com.htv.smartfarm.proto.identity.v1.RevokeAllSessionsRequest;
import com.htv.smartfarm.proto.identity.v1.RevokeAllSessionsResponse;
import com.htv.smartfarm.proto.identity.v1.RevokeSessionRequest;
import com.htv.smartfarm.proto.identity.v1.RevokeSessionResponse;
import com.htv.smartfarm.proto.identity.v1.TokenGrantResponse;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Internal machine-token endpoint. Client secrets and tokens are never logged. */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdentityTokenGrpcService
        extends IdentityTokenServiceGrpc.IdentityTokenServiceImplBase {

    private final ServiceTokenService tokenService;
    private final RefreshSessionAdministrationService sessionService;
    private final GrpcExceptionMapper exceptionMapper;
    private final GrpcCallerProvider callerProvider;

    @Override
    public void issueServiceToken(
            IssueServiceTokenRequest request,
            StreamObserver<TokenGrantResponse> observer
    ) {
        exceptionMapper.executeUnary(observer, () -> {
            requireContext(request.hasContext());
            var grant = tokenService.issue(
                    request.getClientId(),
                    request.getClientSecret(),
                    request.getAudience(),
                    request.getContext().getTenantId(),
                    null
            );
            log.info(
                    "Service token issued through internal gRPC: clientId={}, "
                            + "audience={}, tenantId={}",
                    request.getClientId(),
                    request.getAudience(),
                    request.getContext().getTenantId()
            );
            return response(grant);
        });
    }

    @Override
    public void exchangeDelegatedToken(
            ExchangeDelegatedTokenRequest request,
            StreamObserver<TokenGrantResponse> observer
    ) {
        exceptionMapper.executeUnary(observer, () -> {
            requireContext(request.hasContext());
            requireText(request.getActorSubjectId(), "actor_subject_id");
            GrpcCaller caller = callerProvider.currentCaller();
            if (!caller.isUser()
                    || !caller.subjectId().equals(request.getActorSubjectId())
                    || !caller.tenantId().equals(request.getContext().getTenantId())) {
                throw Status.PERMISSION_DENIED
                        .withDescription(
                                "delegated actor and tenant must match the authenticated user"
                        )
                        .asRuntimeException();
            }
            var grant = tokenService.issue(
                    request.getClientId(),
                    request.getClientSecret(),
                    request.getAudience(),
                    request.getContext().getTenantId(),
                    request.getActorSubjectId()
            );
            log.info(
                    "Delegated service token issued through internal gRPC: "
                            + "clientId={}, audience={}, tenantId={}, actorId={}",
                    request.getClientId(),
                    request.getAudience(),
                    request.getContext().getTenantId(),
                    request.getActorSubjectId()
            );
            return response(grant);
        });
    }

    @Override
    public void listSessions(
            ListSessionsRequest request,
            StreamObserver<ListSessionsResponse> observer
    ) {
        exceptionMapper.executeUnary(observer, () -> {
            requireContext(request.hasContext());
            requireText(request.getSubjectId(), "subject_id");
            var response = ListSessionsResponse.newBuilder();
            sessionService.list(request.getContext().getTenantId(), request.getSubjectId())
                    .forEach(value -> {
                        var item = com.htv.smartfarm.proto.identity.v1.SessionInfo.newBuilder()
                                .setSessionId(value.sessionId()).setSubjectId(value.subjectId())
                                .setTenantId(value.tenantId()).setAudience(value.audience())
                                .setCreatedAt(timestamp(value.createdAt())).setExpiresAt(timestamp(value.expiresAt()));
                        if (value.lastUsedAt()!=null) item.setLastUsedAt(timestamp(value.lastUsedAt()));
                        if (value.revokedAt()!=null) item.setRevokedAt(timestamp(value.revokedAt()));
                        if (value.revokeReason()!=null) item.setRevokeReason(value.revokeReason());
                        response.addSessions(item);
                    });
            return response.build();
        });
    }

    @Override
    public void revokeSession(
            RevokeSessionRequest request,
            StreamObserver<RevokeSessionResponse> observer
    ) {
        exceptionMapper.executeUnary(observer, () -> {
            requireContext(request.hasContext()); requireText(request.getSubjectId(), "subject_id"); requireText(request.getSessionId(), "session_id");
            boolean revoked=sessionService.revoke(request.getContext().getTenantId(),request.getSubjectId(),request.getSessionId(),request.getReason());
            return RevokeSessionResponse.newBuilder().setRevoked(revoked).build();
        });
    }

    @Override
    public void revokeAllSessions(
            RevokeAllSessionsRequest request,
            StreamObserver<RevokeAllSessionsResponse> observer
    ) {
        exceptionMapper.executeUnary(observer, () -> {
            requireContext(request.hasContext()); requireText(request.getSubjectId(), "subject_id");
            int count=sessionService.revokeAll(request.getContext().getTenantId(),request.getSubjectId(),request.getReason());
            return RevokeAllSessionsResponse.newBuilder().setRevokedCount(count).build();
        });
    }

    private TokenGrantResponse response(ServiceTokenService.Grant grant) {
        return TokenGrantResponse.newBuilder()
                .setAccessToken(grant.accessToken())
                .setTokenType(grant.tokenType())
                .setExpiresInSeconds(grant.expiresInSeconds())
                .setAudience(grant.audience())
                .build();
    }

    private com.google.protobuf.Timestamp timestamp(java.time.Instant value) {
        return com.google.protobuf.Timestamp.newBuilder()
                .setSeconds(value.getEpochSecond())
                .setNanos(value.getNano())
                .build();
    }

    private void requireContext(boolean present) {
        if (!present) throw new IllegalArgumentException("request context is required");
    }

    private void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
