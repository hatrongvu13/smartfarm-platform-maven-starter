package com.htv.smartfarm.identity.grpc;

import com.htv.smartfarm.identity.authorization.application
        .PlatformAuthorizationAdministrationService;
import com.htv.smartfarm.identity.grpc.security.GrpcRequestSecurity;
import com.htv.smartfarm.identity.grpc.security.IdentityGrpcAuthorities;
import com.htv.smartfarm.proto.identity.v1
        .CreatePlatformRoleRequest;
import com.htv.smartfarm.proto.identity.v1
        .CreatePlatformRoleResponse;
import com.htv.smartfarm.proto.identity.v1
        .GrantPlatformPermissionRequest;
import com.htv.smartfarm.proto.identity.v1
        .GrantPlatformPermissionResponse;
import com.htv.smartfarm.proto.identity.v1
        .ListPlatformRolesRequest;
import com.htv.smartfarm.proto.identity.v1
        .ListPlatformRolesResponse;
import com.htv.smartfarm.proto.identity.v1
        .PlatformAuthorizationAdministrationServiceGrpc;
import com.htv.smartfarm.proto.identity.v1
        .RevokePlatformPermissionRequest;
import com.htv.smartfarm.proto.identity.v1
        .RevokePlatformPermissionResponse;

import io.grpc.stub.StreamObserver;

import org.springframework.stereotype.Service;

@Service
public class PlatformAuthorizationAdministrationGrpcService
        extends PlatformAuthorizationAdministrationServiceGrpc
        .PlatformAuthorizationAdministrationServiceImplBase {

    private static final String ROLE_PATTERN =
            "[A-Z][A-Z0-9_]{1,39}";

    private static final String PERMISSION_PATTERN =
            "[A-Za-z][A-Za-z0-9:._-]{1,79}";

    private final PlatformAuthorizationAdministrationService
            administrationService;

    private final IdentityAdministrationProtoMapper protoMapper;
    private final GrpcRequestSecurity requestSecurity;
    private final GrpcExceptionMapper exceptionMapper;

    public PlatformAuthorizationAdministrationGrpcService(
            PlatformAuthorizationAdministrationService
                    administrationService,
            IdentityAdministrationProtoMapper protoMapper,
            GrpcRequestSecurity requestSecurity,
            GrpcExceptionMapper exceptionMapper
    ) {
        this.administrationService = administrationService;
        this.protoMapper = protoMapper;
        this.requestSecurity = requestSecurity;
        this.exceptionMapper = exceptionMapper;
    }

    @Override
    public void listPlatformRoles(
            ListPlatformRolesRequest request,
            StreamObserver<ListPlatformRolesResponse>
                    responseObserver
    ) {
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

                    var page =
                            administrationService
                                    .listPlatformRoles(
                                            pageSize(
                                                    request.getPage()
                                                            .getPageSize()
                                            ),
                                            request.getPage()
                                                    .getPageToken(),
                                            request
                                                    .getIncludePermissions()
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

                    var role =
                            administrationService.grantPermission(
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

                    var role =
                            administrationService.revokePermission(
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

    private int pageSize(int requested) {
        if (requested <= 0) {
            return 20;
        }

        return Math.min(requested, 100);
    }

    private void validateRoleCode(String roleCode) {
        if (roleCode == null
                || !roleCode.matches(ROLE_PATTERN)) {
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
            throw new IllegalArgumentException(
                    "Invalid permission code"
            );
        }
    }

    private void requireContext(boolean hasContext) {
        if (!hasContext) {
            throw new IllegalArgumentException(
                    "request context is required"
            );
        }
    }
}