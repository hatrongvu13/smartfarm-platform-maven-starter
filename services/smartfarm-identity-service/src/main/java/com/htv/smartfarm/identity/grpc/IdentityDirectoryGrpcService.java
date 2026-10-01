package com.htv.smartfarm.identity.grpc;

import com.htv.smartfarm.identity.account.application.PrincipalProfileService;
import com.htv.smartfarm.identity.authorization.application.AuthorizationService;
import com.htv.smartfarm.identity.grpc.security.GrpcRequestSecurity;
import com.htv.smartfarm.identity.grpc.security.GrpcRequestSecurity.SecuredRequest;
import com.htv.smartfarm.proto.common.v1.PingRequest;
import com.htv.smartfarm.proto.common.v1.PingResponse;
import com.htv.smartfarm.proto.identity.v1.BatchCheckPermissionsRequest;
import com.htv.smartfarm.proto.identity.v1.BatchCheckPermissionsResponse;
import com.htv.smartfarm.proto.identity.v1.CheckPermissionRequest;
import com.htv.smartfarm.proto.identity.v1.CheckPermissionResponse;
import com.htv.smartfarm.proto.identity.v1.GetPrincipalRequest;
import com.htv.smartfarm.proto.identity.v1.GetPrincipalResponse;
import com.htv.smartfarm.proto.identity.v1.IdentityDirectoryServiceGrpc;
import com.htv.smartfarm.proto.identity.v1.UpdatePrincipalProfileRequest;
import com.htv.smartfarm.proto.identity.v1.UpdatePrincipalProfileResponse;

import io.grpc.stub.StreamObserver;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class IdentityDirectoryGrpcService
        extends IdentityDirectoryServiceGrpc
        .IdentityDirectoryServiceImplBase {

    private static final int MAX_BATCH_TARGETS = 100;

    private static final String SERVICE_NAME =
            "smartfarm-identity-service";

    private final PrincipalProfileService principalProfileService;
    private final AuthorizationService authorizationService;
    private final IdentityProtoMapper protoMapper;
    private final GrpcRequestSecurity requestSecurity;
    private final GrpcExceptionMapper exceptionMapper;
    private final String serviceVersion;

    public IdentityDirectoryGrpcService(
            PrincipalProfileService principalProfileService,
            AuthorizationService authorizationService,
            IdentityProtoMapper protoMapper,
            GrpcRequestSecurity requestSecurity,
            GrpcExceptionMapper exceptionMapper,
            @Value("${spring.application.version:unknown}")
            String serviceVersion
    ) {
        this.principalProfileService = principalProfileService;
        this.authorizationService = authorizationService;
        this.protoMapper = protoMapper;
        this.requestSecurity = requestSecurity;
        this.exceptionMapper = exceptionMapper;
        this.serviceVersion = serviceVersion;
    }

    @Override
    public void getPrincipal(
            GetPrincipalRequest request,
            StreamObserver<GetPrincipalResponse> responseObserver
    ) {
        exceptionMapper.executeUnary(
                responseObserver,
                () -> {
                    requireContext(request.hasContext());

                    SecuredRequest securedRequest =
                            requestSecurity.authorizePrincipalRead(
                                    request.getContext(),
                                    request.getSubjectId()
                            );

                    var principal =
                            principalProfileService.getPrincipal(
                                    securedRequest.tenantId(),
                                    securedRequest.subjectId()
                            );

                    return GetPrincipalResponse.newBuilder()
                            .setPrincipal(
                                    protoMapper.toProto(principal)
                            )
                            .build();
                }
        );
    }

    @Override
    public void updatePrincipalProfile(
            UpdatePrincipalProfileRequest request,
            StreamObserver<UpdatePrincipalProfileResponse> responseObserver
    ) {
        exceptionMapper.executeUnary(
                responseObserver,
                () -> {
                    requireContext(request.hasContext());
                    validateProfileUpdate(request);

                    SecuredRequest securedRequest =
                            requestSecurity.authorizeProfileUpdate(
                                    request.getContext(),
                                    request.getSubjectId()
                            );

                    var command =
                            protoMapper.toUpdateProfileCommand(
                                    securedRequest.subjectId(),
                                    request
                            );

                    var updatedPrincipal =
                            principalProfileService.updateProfile(
                                    securedRequest.tenantId(),
                                    command,
                                    request.getContext().getActorId(),
                                    request.getContext().getCorrelationId()
                            );

                    return UpdatePrincipalProfileResponse.newBuilder()
                            .setPrincipal(
                                    protoMapper.toProto(
                                            updatedPrincipal
                                    )
                            )
                            .build();
                }
        );
    }

    @Override
    public void checkPermission(
            CheckPermissionRequest request,
            StreamObserver<CheckPermissionResponse> responseObserver
    ) {
        exceptionMapper.executeUnary(
                responseObserver,
                () -> {
                    requireContext(request.hasContext());
                    requirePermissionTarget(request);

                    SecuredRequest securedRequest =
                            requestSecurity.authorizePermissionCheck(
                                    request.getContext(),
                                    request.getSubjectId()
                            );

                    var target =
                            protoMapper.toApplicationTarget(
                                    request.getTarget()
                            );

                    var decision =
                            authorizationService.checkPermission(
                                    securedRequest.tenantId(),
                                    securedRequest.subjectId(),
                                    target
                            );

                    return protoMapper.toProto(decision);
                }
        );
    }

    @Override
    public void batchCheckPermissions(
            BatchCheckPermissionsRequest request,
            StreamObserver<BatchCheckPermissionsResponse> responseObserver
    ) {
        exceptionMapper.executeUnary(
                responseObserver,
                () -> {
                    requireContext(request.hasContext());
                    validateBatchRequest(request);

                    SecuredRequest securedRequest =
                            requestSecurity.authorizePermissionCheck(
                                    request.getContext(),
                                    request.getSubjectId()
                            );

                    var targets =
                            protoMapper.toApplicationTargets(
                                    request.getTargetsList()
                            );

                    var decisions =
                            authorizationService
                                    .batchCheckPermissions(
                                            securedRequest.tenantId(),
                                            securedRequest.subjectId(),
                                            targets
                                    );

                    BatchCheckPermissionsResponse.Builder response =
                            BatchCheckPermissionsResponse.newBuilder();

                    decisions.stream()
                            .map(
                                    protoMapper
                                            ::toPermissionDecisionProto
                            )
                            .forEach(response::addDecisions);

                    return response.build();
                }
        );
    }

    @Override
    public void ping(
            PingRequest request,
            StreamObserver<PingResponse> responseObserver
    ) {
        exceptionMapper.executeUnary(
                responseObserver,
                () -> {
                    requestSecurity.authenticatedCaller();

                    return PingResponse.newBuilder()
                            .setService(SERVICE_NAME)
                            .setStatus("UP")
                            .setVersion(serviceVersion)
                            .build();
                }
        );
    }

    private void requireContext(boolean hasContext) {
        if (!hasContext) {
            throw new IllegalArgumentException(
                    "request context is required"
            );
        }
    }

    private void requirePermissionTarget(
            CheckPermissionRequest request
    ) {
        if (!request.hasTarget()) {
            throw new IllegalArgumentException(
                    "target is required"
            );
        }
    }

    private void validateProfileUpdate(
            UpdatePrincipalProfileRequest request
    ) {
        if (!request.hasProfile()) {
            throw new IllegalArgumentException(
                    "profile is required"
            );
        }

        if (!request.hasUpdateMask()
                || request.getUpdateMask()
                .getPathsCount() == 0) {
            throw new IllegalArgumentException(
                    "update_mask must not be empty"
            );
        }

        if (request.getExpectedVersion() < 0) {
            throw new IllegalArgumentException(
                    "expected_version must not be negative"
            );
        }
    }

    private void validateBatchRequest(
            BatchCheckPermissionsRequest request
    ) {
        if (request.getTargetsCount() == 0) {
            throw new IllegalArgumentException(
                    "targets must not be empty"
            );
        }

        if (request.getTargetsCount() > MAX_BATCH_TARGETS) {
            throw new IllegalArgumentException(
                    "A maximum of "
                            + MAX_BATCH_TARGETS
                            + " permission targets is allowed"
            );
        }
    }
}