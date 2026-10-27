package com.htv.farmverse.identity.grpc;

import com.htv.farmverse.identity.account.application
        .PrincipalProfileService;
import com.htv.farmverse.identity.authorization.application
        .AuthorizationService;
import com.htv.farmverse.identity.grpc.security.GrpcRequestSecurity;
import com.htv.farmverse.identity.grpc.security
        .GrpcRequestSecurity.SecuredRequest;
import com.htv.farmverse.proto.common.v1.PingRequest;
import com.htv.farmverse.proto.common.v1.PingResponse;
import com.htv.farmverse.proto.identity.v1
        .BatchCheckPermissionsRequest;
import com.htv.farmverse.proto.identity.v1
        .BatchCheckPermissionsResponse;
import com.htv.farmverse.proto.identity.v1
        .CheckPermissionRequest;
import com.htv.farmverse.proto.identity.v1
        .CheckPermissionResponse;
import com.htv.farmverse.proto.identity.v1
        .GetPrincipalRequest;
import com.htv.farmverse.proto.identity.v1
        .GetPrincipalResponse;
import com.htv.farmverse.proto.identity.v1
        .IdentityDirectoryServiceGrpc;
import com.htv.farmverse.proto.identity.v1
        .UpdatePrincipalProfileRequest;
import com.htv.farmverse.proto.identity.v1
        .UpdatePrincipalProfileResponse;

import io.grpc.stub.StreamObserver;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.grpc.server.service.GrpcService;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@GrpcService
@RequiredArgsConstructor
public class IdentityDirectoryGrpcService
        extends IdentityDirectoryServiceGrpc
        .IdentityDirectoryServiceImplBase {

    private static final int MAX_BATCH_TARGETS = 100;

    private static final String SERVICE_NAME =
            "identity-service";

    private final PrincipalProfileService principalProfileService;
    private final AuthorizationService authorizationService;
    private final IdentityProtoMapper protoMapper;
    private final GrpcRequestSecurity requestSecurity;
    private final GrpcExceptionMapper exceptionMapper;

    @Override
    public void getPrincipal(
            GetPrincipalRequest request,
            StreamObserver<GetPrincipalResponse> responseObserver
    ) {
        log.debug(
                "Received get principal request: subjectId={}",
                request.getSubjectId()
        );

        exceptionMapper.executeUnary(
                responseObserver,
                () -> {
                    requireContext(request.hasContext());

                    SecuredRequest securedRequest =
                            requestSecurity.authorizePrincipalRead(
                                    request.getContext(),
                                    request.getSubjectId()
                            );

                    log.debug(
                            "Get principal request authorized: "
                                    + "tenantId={}, subjectId={}",
                            securedRequest.tenantId(),
                            securedRequest.subjectId()
                    );

                    var principal =
                            principalProfileService.getPrincipal(
                                    securedRequest.tenantId(),
                                    securedRequest.subjectId()
                            );

                    log.debug(
                            "Principal retrieved successfully: "
                                    + "tenantId={}, subjectId={}",
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
            StreamObserver<UpdatePrincipalProfileResponse>
                    responseObserver
    ) {
        log.info(
                "Received update principal profile request: "
                        + "subjectId={}, expectedVersion={}, "
                        + "updateFieldCount={}",
                request.getSubjectId(),
                request.getExpectedVersion(),
                request.hasUpdateMask()
                        ? request.getUpdateMask().getPathsCount()
                        : 0
        );

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

                    log.debug(
                            "Update principal profile request authorized: "
                                    + "tenantId={}, subjectId={}, "
                                    + "actorId={}, correlationId={}",
                            securedRequest.tenantId(),
                            securedRequest.subjectId(),
                            request.getContext().getActorId(),
                            request.getContext().getCorrelationId()
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
                                    request.getContext()
                                            .getCorrelationId()
                            );

                    log.info(
                            "Principal profile updated successfully: "
                                    + "tenantId={}, subjectId={}, "
                                    + "actorId={}, correlationId={}",
                            securedRequest.tenantId(),
                            securedRequest.subjectId(),
                            request.getContext().getActorId(),
                            request.getContext()
                                    .getCorrelationId()
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
            StreamObserver<CheckPermissionResponse>
                    responseObserver
    ) {
        log.debug(
                "Received check permission request: subjectId={}",
                request.getSubjectId()
        );

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

                    log.debug(
                            "Check permission request authorized: "
                                    + "tenantId={}, subjectId={}",
                            securedRequest.tenantId(),
                            securedRequest.subjectId()
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

                    log.debug(
                            "Permission check completed: "
                                    + "tenantId={}, subjectId={}",
                            securedRequest.tenantId(),
                            securedRequest.subjectId()
                    );

                    return protoMapper.toProto(decision);
                }
        );
    }

    @Override
    public void batchCheckPermissions(
            BatchCheckPermissionsRequest request,
            StreamObserver<BatchCheckPermissionsResponse>
                    responseObserver
    ) {
        log.debug(
                "Received batch check permissions request: "
                        + "subjectId={}, targetCount={}",
                request.getSubjectId(),
                request.getTargetsCount()
        );

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

                    log.debug(
                            "Batch check permissions request authorized: "
                                    + "tenantId={}, subjectId={}, "
                                    + "targetCount={}",
                            securedRequest.tenantId(),
                            securedRequest.subjectId(),
                            request.getTargetsCount()
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

                    log.debug(
                            "Batch permission check completed: "
                                    + "tenantId={}, subjectId={}, "
                                    + "targetCount={}, decisionCount={}",
                            securedRequest.tenantId(),
                            securedRequest.subjectId(),
                            request.getTargetsCount(),
                            decisions.size()
                    );

                    return response.build();
                }
        );
    }

    @Override
    public void ping(
            PingRequest request,
            StreamObserver<PingResponse> responseObserver
    ) {
        log.debug("Received authenticated ping request");

        exceptionMapper.executeUnary(
                responseObserver,
                () -> {
                    requestSecurity.authenticatedCaller();

                    log.debug(
                            "Authenticated ping request completed: "
                                    + "service={}, status=UP",
                            SERVICE_NAME
                    );

                    return PingResponse.newBuilder()
                            .setService(SERVICE_NAME)
                            .setStatus("UP")
                            .build();
                }
        );
    }

    private void requireContext(boolean hasContext) {
        if (!hasContext) {
            log.warn(
                    "Identity directory request rejected: "
                            + "request context is missing"
            );

            throw new IllegalArgumentException(
                    "request context is required"
            );
        }
    }

    private void requirePermissionTarget(
            CheckPermissionRequest request
    ) {
        if (!request.hasTarget()) {
            log.warn(
                    "Permission check request rejected: "
                            + "target is missing, subjectId={}",
                    request.getSubjectId()
            );

            throw new IllegalArgumentException(
                    "target is required"
            );
        }
    }

    private void validateProfileUpdate(
            UpdatePrincipalProfileRequest request
    ) {
        if (!request.hasProfile()) {
            log.warn(
                    "Profile update request rejected: "
                            + "profile is missing, subjectId={}",
                    request.getSubjectId()
            );

            throw new IllegalArgumentException(
                    "profile is required"
            );
        }

        if (!request.hasUpdateMask()
                || request.getUpdateMask()
                .getPathsCount() == 0) {
            log.warn(
                    "Profile update request rejected: "
                            + "update mask is empty, subjectId={}",
                    request.getSubjectId()
            );

            throw new IllegalArgumentException(
                    "update_mask must not be empty"
            );
        }

        if (request.getExpectedVersion() < 0) {
            log.warn(
                    "Profile update request rejected: "
                            + "expected version is negative, "
                            + "subjectId={}, expectedVersion={}",
                    request.getSubjectId(),
                    request.getExpectedVersion()
            );

            throw new IllegalArgumentException(
                    "expected_version must not be negative"
            );
        }
    }

    private void validateBatchRequest(
            BatchCheckPermissionsRequest request
    ) {
        if (request.getTargetsCount() == 0) {
            log.warn(
                    "Batch permission check request rejected: "
                            + "targets are empty, subjectId={}",
                    request.getSubjectId()
            );

            throw new IllegalArgumentException(
                    "targets must not be empty"
            );
        }

        if (request.getTargetsCount() > MAX_BATCH_TARGETS) {
            log.warn(
                    "Batch permission check request rejected: "
                            + "too many targets, subjectId={}, "
                            + "targetCount={}, maximum={}",
                    request.getSubjectId(),
                    request.getTargetsCount(),
                    MAX_BATCH_TARGETS
            );

            throw new IllegalArgumentException(
                    "A maximum of "
                            + MAX_BATCH_TARGETS
                            + " permission targets is allowed"
            );
        }
    }
}