package com.htv.smartfarm.identity.grpc;

import com.htv.smartfarm.identity.administration.application.TenantIdentityAdministrationService;
import com.htv.smartfarm.identity.administration.application.command.AssignRoleCommand;
import com.htv.smartfarm.identity.administration.application.command.CreateTenantUserCommand;
import com.htv.smartfarm.identity.administration.application.command.RevokeRoleCommand;
import com.htv.smartfarm.identity.authorization.application.AuthorizationCatalogService;
import com.htv.smartfarm.identity.grpc.security.GrpcRequestSecurity;
import com.htv.smartfarm.identity.grpc.security.IdentityGrpcAuthorities;
import com.htv.smartfarm.proto.identity.v1.AssignRoleRequest;
import com.htv.smartfarm.proto.identity.v1.AssignRoleResponse;
import com.htv.smartfarm.proto.identity.v1.CreateUserRequest;
import com.htv.smartfarm.proto.identity.v1.CreateUserResponse;
import com.htv.smartfarm.proto.identity.v1.DisableMembershipRequest;
import com.htv.smartfarm.proto.identity.v1.EnableMembershipRequest;
import com.htv.smartfarm.proto.identity.v1.GetRoleRequest;
import com.htv.smartfarm.proto.identity.v1.GetRoleResponse;
import com.htv.smartfarm.proto.identity.v1.GetUserAuthorizationRequest;
import com.htv.smartfarm.proto.identity.v1.GetUserAuthorizationResponse;
import com.htv.smartfarm.proto.identity.v1.IdentityAdministrationServiceGrpc;
import com.htv.smartfarm.proto.identity.v1.ListPermissionsRequest;
import com.htv.smartfarm.proto.identity.v1.ListPermissionsResponse;
import com.htv.smartfarm.proto.identity.v1.ListRolesRequest;
import com.htv.smartfarm.proto.identity.v1.ListRolesResponse;
import com.htv.smartfarm.proto.identity.v1.ListUsersRequest;
import com.htv.smartfarm.proto.identity.v1.ListUsersResponse;
import com.htv.smartfarm.proto.identity.v1.RevokeRoleRequest;
import com.htv.smartfarm.proto.identity.v1.RevokeRoleResponse;
import com.htv.smartfarm.proto.identity.v1.SuspendMembershipRequest;
import com.htv.smartfarm.proto.identity.v1.UpdateMembershipStatusResponse;

import io.grpc.stub.StreamObserver;

import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class IdentityAdministrationGrpcService
        extends IdentityAdministrationServiceGrpc
        .IdentityAdministrationServiceImplBase {

    private final TenantIdentityAdministrationService
            administrationService;

    private final AuthorizationCatalogService catalogService;
    private final IdentityAdministrationProtoMapper protoMapper;
    private final GrpcRequestSecurity requestSecurity;
    private final GrpcExceptionMapper exceptionMapper;

    public IdentityAdministrationGrpcService(
            TenantIdentityAdministrationService administrationService,
            AuthorizationCatalogService catalogService,
            IdentityAdministrationProtoMapper protoMapper,
            GrpcRequestSecurity requestSecurity,
            GrpcExceptionMapper exceptionMapper
    ) {
        this.administrationService = administrationService;
        this.catalogService = catalogService;
        this.protoMapper = protoMapper;
        this.requestSecurity = requestSecurity;
        this.exceptionMapper = exceptionMapper;
    }

    @Override
    public void listRoles(
            ListRolesRequest request,
            StreamObserver<ListRolesResponse> responseObserver
    ) {
        exceptionMapper.executeUnary(
                responseObserver,
                () -> {
                    requireContext(request.hasContext());

                    var secured =
                            requestSecurity
                                    .authorizeTenantAdministration(
                                            request.getContext(),
                                            IdentityGrpcAuthorities
                                                    .ROLE_READ
                                    );

                    var page = administrationService.listRoles(
                            secured.tenantId(),
                            pageSize(request.getPage().getPageSize()),
                            request.getPage().getPageToken(),
                            request.getIncludePermissions()
                    );

                    return protoMapper.toListRolesResponse(page);
                }
        );
    }

    @Override
    public void getRole(
            GetRoleRequest request,
            StreamObserver<GetRoleResponse> responseObserver
    ) {
        exceptionMapper.executeUnary(
                responseObserver,
                () -> {
                    requireContext(request.hasContext());

                    var secured =
                            requestSecurity
                                    .authorizeTenantAdministration(
                                            request.getContext(),
                                            IdentityGrpcAuthorities
                                                    .ROLE_READ
                                    );

                    String roleId = request.hasRoleId()
                            ? request.getRoleId()
                            : null;

                    String roleCode = request.hasRoleCode()
                            ? request.getRoleCode()
                            : null;

                    if (roleId == null && roleCode == null) {
                        throw new IllegalArgumentException(
                                "role_id or role_code is required"
                        );
                    }

                    var role = administrationService.getRole(
                            secured.tenantId(),
                            roleId,
                            roleCode
                    );

                    return GetRoleResponse.newBuilder()
                            .setRole(protoMapper.toProto(role))
                            .build();
                }
        );
    }

    @Override
    public void listPermissions(
            ListPermissionsRequest request,
            StreamObserver<ListPermissionsResponse>
                    responseObserver
    ) {
        exceptionMapper.executeUnary(
                responseObserver,
                () -> {
                    requireContext(request.hasContext());

                    requestSecurity.authorizeTenantAdministration(
                            request.getContext(),
                            IdentityGrpcAuthorities.PERMISSION_READ
                    );

                    var page = catalogService.listPermissions(
                            pageSize(request.getPage().getPageSize()),
                            request.getPage().getPageToken(),
                            request.getResourceType()
                    );

                    return protoMapper
                            .toListPermissionsResponse(page);
                }
        );
    }

    @Override
    public void listUsers(
            ListUsersRequest request,
            StreamObserver<ListUsersResponse> responseObserver
    ) {
        exceptionMapper.executeUnary(
                responseObserver,
                () -> {
                    requireContext(request.hasContext());

                    var secured =
                            requestSecurity
                                    .authorizeTenantAdministration(
                                            request.getContext(),
                                            IdentityGrpcAuthorities
                                                    .USER_READ
                                    );

                    var page = administrationService.listUsers(
                            secured.tenantId(),
                            pageSize(request.getPage().getPageSize()),
                            request.getPage().getPageToken(),
                            request.getSearchText(),
                            protoMapper.toDomainStatus(
                                    request.getMembershipStatus()
                            ),
                            request.getRoleCode()
                    );

                    return protoMapper.toListUsersResponse(page);
                }
        );
    }

    @Override
    public void getUserAuthorization(
            GetUserAuthorizationRequest request,
            StreamObserver<GetUserAuthorizationResponse>
                    responseObserver
    ) {
        exceptionMapper.executeUnary(
                responseObserver,
                () -> {
                    requireContext(request.hasContext());
                    requireText(
                            request.getSubjectId(),
                            "subject_id"
                    );

                    var secured =
                            requestSecurity
                                    .authorizeTenantAdministration(
                                            request.getContext(),
                                            IdentityGrpcAuthorities
                                                    .USER_READ
                                    );

                    var authorization =
                            administrationService
                                    .getUserAuthorization(
                                            secured.tenantId(),
                                            request.getSubjectId()
                                    );

                    return GetUserAuthorizationResponse
                            .newBuilder()
                            .setAuthorization(
                                    protoMapper.toProto(
                                            authorization
                                    )
                            )
                            .build();
                }
        );
    }

    @Override
    public void createUser(
            CreateUserRequest request,
            StreamObserver<CreateUserResponse> responseObserver
    ) {
        exceptionMapper.executeUnary(
                responseObserver,
                () -> {
                    requireContext(request.hasContext());
                    validateCreateUser(request);

                    var secured =
                            requestSecurity
                                    .authorizeTenantAdministration(
                                            request.getContext(),
                                            IdentityGrpcAuthorities
                                                    .USER_CREATE
                                    );

                    List<String> roles =
                            request.getInitialRoleCodesList()
                                    .isEmpty()
                                    ? List.of("USER")
                                    : request.getInitialRoleCodesList();

                    var result =
                            administrationService.createUser(
                                    new CreateTenantUserCommand(
                                            secured.tenantId(),
                                            secured.actorId(),
                                            request.getEmail(),
                                            request.getInitialPassword(),
                                            request.getDisplayName(),
                                            request.getPhoneNumber(),
                                            request.getLocale(),
                                            request.getTimeZone(),
                                            roles,
                                            request.getActivateImmediately()
                                    )
                            );

                    return protoMapper.toCreateUserResponse(result);
                }
        );
    }

    @Override
    public void assignRole(
            AssignRoleRequest request,
            StreamObserver<AssignRoleResponse> responseObserver
    ) {
        exceptionMapper.executeUnary(
                responseObserver,
                () -> {
                    requireContext(request.hasContext());
                    requireText(
                            request.getSubjectId(),
                            "subject_id"
                    );
                    validateTenantRole(request.getRoleCode());

                    var secured =
                            requestSecurity
                                    .authorizeTenantAdministration(
                                            request.getContext(),
                                            IdentityGrpcAuthorities
                                                    .ROLE_ASSIGN
                                    );

                    var authorization =
                            administrationService.assignRole(
                                    new AssignRoleCommand(
                                            secured.tenantId(),
                                            request.getSubjectId(),
                                            request.getRoleCode(),
                                            secured.actorId()
                                    )
                            );

                    return protoMapper.toAssignRoleResponse(
                            authorization
                    );
                }
        );
    }

    @Override
    public void revokeRole(
            RevokeRoleRequest request,
            StreamObserver<RevokeRoleResponse> responseObserver
    ) {
        exceptionMapper.executeUnary(
                responseObserver,
                () -> {
                    requireContext(request.hasContext());
                    requireText(
                            request.getSubjectId(),
                            "subject_id"
                    );
                    validateTenantRole(request.getRoleCode());

                    var secured =
                            requestSecurity
                                    .authorizeTenantAdministration(
                                            request.getContext(),
                                            IdentityGrpcAuthorities
                                                    .ROLE_ASSIGN
                                    );

                    if (request.getSubjectId()
                            .equals(secured.actorId())
                            && "ADMIN".equals(
                            request.getRoleCode()
                    )) {
                        throw new IllegalStateException(
                                "Cannot remove own ADMIN role"
                        );
                    }

                    var authorization =
                            administrationService.revokeRole(
                                    new RevokeRoleCommand(
                                            secured.tenantId(),
                                            request.getSubjectId(),
                                            request.getRoleCode(),
                                            secured.actorId()
                                    )
                            );

                    return protoMapper.toRevokeRoleResponse(
                            authorization
                    );
                }
        );
    }

    @Override
    public void disableMembership(
            DisableMembershipRequest request,
            StreamObserver<UpdateMembershipStatusResponse>
                    responseObserver
    ) {
        exceptionMapper.executeUnary(
                responseObserver,
                () -> {
                    requireContext(request.hasContext());
                    requireText(
                            request.getSubjectId(),
                            "subject_id"
                    );

                    var secured =
                            requestSecurity
                                    .authorizeTenantAdministration(
                                            request.getContext(),
                                            IdentityGrpcAuthorities
                                                    .USER_DISABLE
                                    );

                    if (request.getSubjectId()
                            .equals(secured.actorId())) {
                        throw new IllegalStateException(
                                "Cannot disable own membership"
                        );
                    }

                    var authorization =
                            administrationService
                                    .disableMembership(
                                            secured.tenantId(),
                                            request.getSubjectId(),
                                            secured.actorId(),
                                            request.getReason()
                                    );

                    return protoMapper
                            .toMembershipStatusResponse(
                                    authorization
                            );
                }
        );
    }

    @Override
    public void enableMembership(
            EnableMembershipRequest request,
            StreamObserver<UpdateMembershipStatusResponse>
                    responseObserver
    ) {
        exceptionMapper.executeUnary(
                responseObserver,
                () -> {
                    requireContext(request.hasContext());
                    requireText(
                            request.getSubjectId(),
                            "subject_id"
                    );

                    var secured =
                            requestSecurity
                                    .authorizeTenantAdministration(
                                            request.getContext(),
                                            IdentityGrpcAuthorities
                                                    .USER_DISABLE
                                    );

                    var authorization =
                            administrationService
                                    .enableMembership(
                                            secured.tenantId(),
                                            request.getSubjectId(),
                                            secured.actorId()
                                    );

                    return protoMapper
                            .toMembershipStatusResponse(
                                    authorization
                            );
                }
        );
    }

    @Override
    public void suspendMembership(
            SuspendMembershipRequest request,
            StreamObserver<UpdateMembershipStatusResponse>
                    responseObserver
    ) {
        exceptionMapper.executeUnary(
                responseObserver,
                () -> {
                    requireContext(request.hasContext());
                    requireText(
                            request.getSubjectId(),
                            "subject_id"
                    );

                    var secured =
                            requestSecurity
                                    .authorizeTenantAdministration(
                                            request.getContext(),
                                            IdentityGrpcAuthorities
                                                    .USER_DISABLE
                                    );

                    if (request.getSubjectId()
                            .equals(secured.actorId())) {
                        throw new IllegalStateException(
                                "Cannot suspend own membership"
                        );
                    }

                    var authorization =
                            administrationService
                                    .suspendMembership(
                                            secured.tenantId(),
                                            request.getSubjectId(),
                                            secured.actorId(),
                                            request.getReason()
                                    );

                    return protoMapper
                            .toMembershipStatusResponse(
                                    authorization
                            );
                }
        );
    }

    private int pageSize(int requestedPageSize) {
        if (requestedPageSize <= 0) {
            return 20;
        }

        return Math.min(requestedPageSize, 100);
    }

    private void validateCreateUser(
            CreateUserRequest request
    ) {
        requireText(request.getEmail(), "email");
        requireText(
                request.getInitialPassword(),
                "initial_password"
        );

        if (request.getInitialPassword().length() < 12) {
            throw new IllegalArgumentException(
                    "initial_password must contain at least "
                            + "12 characters"
            );
        }

        if (request.getInitialPassword().length() > 128) {
            throw new IllegalArgumentException(
                    "initial_password must not exceed "
                            + "128 characters"
            );
        }
    }

    private void validateTenantRole(String roleCode) {
        requireText(roleCode, "role_code");

        String normalized = roleCode.trim().toUpperCase();

        if ("PLATFORM_ADMIN".equals(normalized)
                || "SUPERADMIN".equals(normalized)) {
            throw new IllegalArgumentException(
                    "Platform roles cannot be delegated "
                            + "through tenant administration"
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

    private void requireText(
            String value,
            String field
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    field + " must not be blank"
            );
        }
    }
}