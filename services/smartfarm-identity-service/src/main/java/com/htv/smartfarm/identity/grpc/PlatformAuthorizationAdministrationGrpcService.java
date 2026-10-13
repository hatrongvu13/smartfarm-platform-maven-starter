package com.htv.smartfarm.identity.grpc;

import com.htv.smartfarm.identity.authorization.application
        .PlatformAuthorizationAdministrationService;
import com.htv.smartfarm.identity.authorization.application
        .GlobalPermissionCatalogAdministrationService;
import com.htv.smartfarm.identity.authorization.domain.PermissionCode;
import com.htv.smartfarm.identity.grpc.security.GrpcRequestSecurity;
import com.htv.smartfarm.identity.grpc.security.IdentityGrpcAuthorities;
import com.htv.smartfarm.proto.identity.v1.*;

import io.grpc.stub.StreamObserver;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformAuthorizationAdministrationGrpcService
        extends PlatformAuthorizationAdministrationServiceGrpc
        .PlatformAuthorizationAdministrationServiceImplBase {

    private static final String ROLE_PATTERN =
            "[A-Z][A-Z0-9_]{1,39}";

    private static final String PERMISSION_PATTERN =
            "[A-Za-z][A-Za-z0-9:._-]{1,79}";

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final PlatformAuthorizationAdministrationService
            administrationService;
    private final GlobalPermissionCatalogAdministrationService
            globalPermissions;

    private final IdentityAdministrationProtoMapper protoMapper;
    private final GrpcRequestSecurity requestSecurity;
    private final GrpcExceptionMapper exceptionMapper;

    @Override
    public void listPlatformRoles(
            ListPlatformRolesRequest request,
            StreamObserver<ListPlatformRolesResponse>
                    responseObserver
    ) {
        log.debug(
                "Received list platform roles request: "
                        + "requestedPageSize={}, includePermissions={}",
                request.getPage().getPageSize(),
                request.getIncludePermissions()
        );

        exceptionMapper.executeUnary(
                responseObserver,
                () -> {
                    requireContext(request.hasContext());

                    requestSecurity
                            .authorizePlatformAdministration(
                                    request.getContext(),
                                    IdentityGrpcAuthorities
                                            .PLATFORM_ROLE_MANAGE
                            );

                    int effectivePageSize = pageSize(
                            request.getPage().getPageSize()
                    );

                    var page =
                            administrationService
                                    .listPlatformRoles(
                                            effectivePageSize,
                                            request.getPage()
                                                    .getPageToken(),
                                            request
                                                    .getIncludePermissions()
                                    );

                    log.debug(
                            "Listed platform roles successfully: "
                                    + "pageSize={}, includePermissions={}",
                            effectivePageSize,
                            request.getIncludePermissions()
                    );

                    return protoMapper
                            .toListPlatformRolesResponse(page);
                }
        );
    }

    @Override
    public void createPlatformRole(
            CreatePlatformRoleRequest request,
            StreamObserver<CreatePlatformRoleResponse>
                    responseObserver
    ) {
        log.info(
                "Received create platform role request: roleCode={}",
                request.getRoleCode()
        );

        exceptionMapper.executeUnary(
                responseObserver,
                () -> {
                    requireContext(request.hasContext());
                    validateRoleCode(request.getRoleCode());

                    var secured =
                            requestSecurity
                                    .authorizePlatformAdministration(
                                            request.getContext(),
                                            IdentityGrpcAuthorities
                                                    .PLATFORM_ROLE_MANAGE
                                    );

                    log.debug(
                            "Create platform role request authorized: "
                                    + "roleCode={}, actorId={}",
                            request.getRoleCode(),
                            secured.actorId()
                    );

                    var role =
                            administrationService
                                    .createPlatformRole(
                                            request.getRoleCode(),
                                            request.getRoleName(),
                                            request.getDescription(),
                                            request
                                                    .getPermissionCodesList(),
                                            secured.actorId()
                                    );

                    log.info(
                            "Platform role created successfully: "
                                    + "roleCode={}, actorId={}",
                            request.getRoleCode(),
                            secured.actorId()
                    );

                    return CreatePlatformRoleResponse
                            .newBuilder()
                            .setRole(protoMapper.toProto(role))
                            .build();
                }
        );
    }

    @Override
    public void grantPlatformPermission(
            GrantPlatformPermissionRequest request,
            StreamObserver<GrantPlatformPermissionResponse>
                    responseObserver
    ) {
        log.info(
                "Received grant platform permission request: "
                        + "roleCode={}, permissionCode={}",
                request.getRoleCode(),
                request.getPermissionCode()
        );

        exceptionMapper.executeUnary(
                responseObserver,
                () -> {
                    requireContext(request.hasContext());
                    validateRoleCode(request.getRoleCode());
                    validatePermissionCode(
                            request.getPermissionCode()
                    );

                    var secured =
                            requestSecurity
                                    .authorizePlatformAdministration(
                                            request.getContext(),
                                            IdentityGrpcAuthorities
                                                    .PLATFORM_PERMISSION_MANAGE
                                    );

                    log.debug(
                            "Grant platform permission request authorized: "
                                    + "roleCode={}, permissionCode={}, "
                                    + "actorId={}",
                            request.getRoleCode(),
                            request.getPermissionCode(),
                            secured.actorId()
                    );

                    var role =
                            administrationService.grantPermission(
                                    request.getRoleCode(),
                                    request.getPermissionCode(),
                                    secured.actorId()
                            );

                    log.info(
                            "Platform permission granted successfully: "
                                    + "roleCode={}, permissionCode={}, "
                                    + "actorId={}",
                            request.getRoleCode(),
                            request.getPermissionCode(),
                            secured.actorId()
                    );

                    return GrantPlatformPermissionResponse
                            .newBuilder()
                            .setRole(protoMapper.toProto(role))
                            .build();
                }
        );
    }

    @Override
    public void revokePlatformPermission(
            RevokePlatformPermissionRequest request,
            StreamObserver<RevokePlatformPermissionResponse>
                    responseObserver
    ) {
        log.info(
                "Received revoke platform permission request: "
                        + "roleCode={}, permissionCode={}",
                request.getRoleCode(),
                request.getPermissionCode()
        );

        exceptionMapper.executeUnary(
                responseObserver,
                () -> {
                    requireContext(request.hasContext());
                    validateRoleCode(request.getRoleCode());
                    validatePermissionCode(
                            request.getPermissionCode()
                    );

                    var secured =
                            requestSecurity
                                    .authorizePlatformAdministration(
                                            request.getContext(),
                                            IdentityGrpcAuthorities
                                                    .PLATFORM_PERMISSION_MANAGE
                                    );

                    log.debug(
                            "Revoke platform permission request authorized: "
                                    + "roleCode={}, permissionCode={}, "
                                    + "actorId={}",
                            request.getRoleCode(),
                            request.getPermissionCode(),
                            secured.actorId()
                    );

                    var role =
                            administrationService.revokePermission(
                                    request.getRoleCode(),
                                    request.getPermissionCode(),
                                    secured.actorId()
                            );

                    log.info(
                            "Platform permission revoked successfully: "
                                    + "roleCode={}, permissionCode={}, "
                                    + "actorId={}",
                            request.getRoleCode(),
                            request.getPermissionCode(),
                            secured.actorId()
                    );

                    return RevokePlatformPermissionResponse
                            .newBuilder()
                            .setRole(protoMapper.toProto(role))
                            .build();
                }
        );
    }

    @Override
    public void getGlobalPermission(
            GetGlobalPermissionRequest request,
            StreamObserver<GetGlobalPermissionResponse> observer
    ) {
        exceptionMapper.executeUnary(observer, () -> {
            requireContext(request.hasContext());
            requestSecurity.authorizePlatformAdministration(
                    request.getContext(),
                    IdentityGrpcAuthorities.PLATFORM_PERMISSION_READ
            );
            var value = globalPermissions.get(request.getPermissionCode());
            return GetGlobalPermissionResponse.newBuilder()
                    .setPermission(permission(value))
                    .build();
        });
    }

    @Override
    public void createGlobalPermission(
            CreateGlobalPermissionRequest request,
            StreamObserver<GlobalPermissionMutationResponse> observer
    ) {
        exceptionMapper.executeUnary(observer, () -> {
            requireContext(request.hasContext());
            PermissionCode.normalize(request.getCode());
            var secured = requestSecurity.authorizePlatformAdministration(
                    request.getContext(),
                    IdentityGrpcAuthorities.PLATFORM_PERMISSION_MANAGE
            );
            var value = globalPermissions.create(
                    request.getCode(), request.getResourceType(),
                    request.getAction(), request.getDescription(),
                    secured.actorId(), request.getContext().getCorrelationId()
            );
            return mutation(value);
        });
    }

    @Override
    public void updateGlobalPermission(
            UpdateGlobalPermissionRequest request,
            StreamObserver<GlobalPermissionMutationResponse> observer
    ) {
        exceptionMapper.executeUnary(observer, () -> {
            requireContext(request.hasContext());
            PermissionCode.normalize(request.getCode());
            var secured = requestSecurity.authorizePlatformAdministration(
                    request.getContext(),
                    IdentityGrpcAuthorities.PLATFORM_PERMISSION_MANAGE
            );
            var value = globalPermissions.update(
                    request.getCode(), request.getResourceType(),
                    request.getAction(), request.getDescription(),
                    request.hasExpectedVersion() ? request.getExpectedVersion() : null,
                    secured.actorId(), request.getContext().getCorrelationId()
            );
            return mutation(value);
        });
    }

    @Override
    public void deleteGlobalPermission(
            DeleteGlobalPermissionRequest request,
            StreamObserver<DeleteAuthorizationObjectResponse> observer
    ) {
        exceptionMapper.executeUnary(observer, () -> {
            requireContext(request.hasContext());
            PermissionCode.normalize(request.getCode());
            var secured = requestSecurity.authorizePlatformAdministration(
                    request.getContext(),
                    IdentityGrpcAuthorities.PLATFORM_PERMISSION_MANAGE
            );
            boolean deleted = globalPermissions.delete(
                    request.getCode(),
                    request.hasExpectedVersion() ? request.getExpectedVersion() : null,
                    secured.actorId(), request.getContext().getCorrelationId()
            );
            return DeleteAuthorizationObjectResponse.newBuilder()
                    .setDeleted(deleted).build();
        });
    }

    private GlobalPermissionMutationResponse mutation(
            com.htv.smartfarm.identity.authorization.domain.PermissionEntity value
    ) {
        return GlobalPermissionMutationResponse.newBuilder()
                .setPermission(permission(value))
                .setVersion(value.getVersion())
                .build();
    }

    private PermissionInfo permission(
            com.htv.smartfarm.identity.authorization.domain.PermissionEntity value
    ) {
        return PermissionInfo.newBuilder()
                .setCode(value.getCode())
                .setResourceType(value.getResourceType())
                .setAction(value.getAction())
                .setDescription(value.getDescription() == null ? "" : value.getDescription())
                .build();
    }

    private PermissionInfo permission(
            com.htv.smartfarm.identity.authorization.application.model.PermissionData value
    ) {
        return PermissionInfo.newBuilder()
                .setCode(value.code())
                .setResourceType(value.resourceType())
                .setAction(value.action())
                .setDescription(value.description() == null ? "" : value.description())
                .build();
    }

    private int pageSize(int requested) {
        if (requested <= 0) {
            log.debug(
                    "Using default page size: requested={}, default={}",
                    requested,
                    DEFAULT_PAGE_SIZE
            );

            return DEFAULT_PAGE_SIZE;
        }

        if (requested > MAX_PAGE_SIZE) {
            log.debug(
                    "Limiting requested page size: requested={}, maximum={}",
                    requested,
                    MAX_PAGE_SIZE
            );

            return MAX_PAGE_SIZE;
        }

        return requested;
    }

    private void validateRoleCode(String roleCode) {
        if (roleCode == null
                || !roleCode.matches(ROLE_PATTERN)) {
            log.warn(
                    "Platform authorization request rejected: "
                            + "invalid role code"
            );

            throw new IllegalArgumentException(
                    "Invalid platform role code"
            );
        }
    }

    private void validatePermissionCode(
            String permissionCode
    ) {
        if (permissionCode == null
                || !permissionCode.matches(
                PERMISSION_PATTERN
        )) {
            log.warn(
                    "Platform authorization request rejected: "
                            + "invalid permission code"
            );

            throw new IllegalArgumentException(
                    "Invalid permission code"
            );
        }
    }

    private void requireContext(boolean hasContext) {
        if (!hasContext) {
            log.warn(
                    "Platform authorization request rejected: "
                            + "request context is missing"
            );

            throw new IllegalArgumentException(
                    "Request context is required"
            );
        }
    }
}