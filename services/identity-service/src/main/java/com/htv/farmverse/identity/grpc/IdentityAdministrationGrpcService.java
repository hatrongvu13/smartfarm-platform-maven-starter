package com.htv.farmverse.identity.grpc;

import com.htv.farmverse.identity.account.application.PasswordPolicy;
import com.htv.farmverse.identity.account.application.PrincipalProfileService;
import com.htv.farmverse.identity.administration.application.TenantIdentityAdministrationService;
import com.htv.farmverse.identity.administration.application.command.AssignRoleCommand;
import com.htv.farmverse.identity.administration.application.command.CreateTenantUserCommand;
import com.htv.farmverse.identity.administration.application.command.RevokeRoleCommand;
import com.htv.farmverse.identity.authorization.application.AuthorizationCatalogService;
import com.htv.farmverse.identity.authorization.application.DynamicAuthorizationAdministrationService;
import com.htv.farmverse.identity.grpc.security.GrpcRequestSecurity;
import com.htv.farmverse.identity.grpc.security.IdentityGrpcAuthorities;
import com.htv.farmverse.proto.identity.v1.*;

import io.grpc.stub.StreamObserver;

import java.util.List;
import java.util.Locale;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.grpc.server.service.GrpcService;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@GrpcService
@RequiredArgsConstructor
public class IdentityAdministrationGrpcService
        extends IdentityAdministrationServiceGrpc
        .IdentityAdministrationServiceImplBase {

    private final TenantIdentityAdministrationService
            administrationService;

    private final AuthorizationCatalogService catalogService;
    private final DynamicAuthorizationAdministrationService dynamicAuthorization;
    private final PrincipalProfileService principalProfileService;
    private final IdentityAdministrationProtoMapper protoMapper;
    private final IdentityProtoMapper identityProtoMapper;
    private final GrpcRequestSecurity requestSecurity;
    private final GrpcExceptionMapper exceptionMapper;

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    @Override
    public void listRoles(
            ListRolesRequest request,
            StreamObserver<ListRolesResponse> responseObserver
    ) {
        log.debug(
                "Received list tenant roles request: "
                        + "requestedPageSize={}, includePermissions={}",
                request.getPage().getPageSize(),
                request.getIncludePermissions()
        );

        exceptionMapper.executeUnary(
                responseObserver,
                () -> {
                    requireContext(request.hasContext());

                    var secured =
                            requestSecurity.authorizeTenantAdministration(
                                    request.getContext(),
                                    IdentityGrpcAuthorities.ROLE_READ
                            );

                    int effectivePageSize =
                            pageSize(request.getPage().getPageSize());

                    var page = administrationService.listRoles(
                            secured.tenantId(),
                            effectivePageSize,
                            request.getPage().getPageToken(),
                            request.getIncludePermissions()
                    );

                    log.debug(
                            "Tenant roles listed successfully: "
                                    + "tenantId={}, actorId={}, pageSize={}, "
                                    + "includePermissions={}, correlationId={}",
                            secured.tenantId(),
                            secured.actorId(),
                            effectivePageSize,
                            request.getIncludePermissions(),
                            request.getContext().getCorrelationId()
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
        log.debug(
                "Received get tenant role request: "
                        + "lookupByRoleId={}, lookupByRoleCode={}",
                request.hasRoleId(),
                request.hasRoleCode()
        );

        exceptionMapper.executeUnary(
                responseObserver,
                () -> {
                    requireContext(request.hasContext());

                    var secured =
                            requestSecurity.authorizeTenantAdministration(
                                    request.getContext(),
                                    IdentityGrpcAuthorities.ROLE_READ
                            );

                    String roleId = request.hasRoleId()
                            ? request.getRoleId()
                            : null;

                    String roleCode = request.hasRoleCode()
                            ? request.getRoleCode()
                            : null;

                    if (roleId == null && roleCode == null) {
                        log.warn(
                                "Get tenant role request rejected: "
                                        + "role identifier is missing, tenantId={}, "
                                        + "actorId={}, correlationId={}",
                                secured.tenantId(),
                                secured.actorId(),
                                request.getContext().getCorrelationId()
                        );

                        throw new IllegalArgumentException(
                                "role_id or role_code is required"
                        );
                    }

                    var role = administrationService.getRole(
                            secured.tenantId(),
                            roleId,
                            roleCode
                    );

                    log.debug(
                            "Tenant role retrieved successfully: "
                                    + "tenantId={}, actorId={}, roleIdPresent={}, "
                                    + "roleCodePresent={}, correlationId={}",
                            secured.tenantId(),
                            secured.actorId(),
                            roleId != null,
                            roleCode != null,
                            request.getContext().getCorrelationId()
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
            StreamObserver<ListPermissionsResponse> responseObserver
    ) {
        log.debug(
                "Received list permissions request: "
                        + "requestedPageSize={}, resourceType={}",
                request.getPage().getPageSize(),
                request.getResourceType()
        );

        exceptionMapper.executeUnary(
                responseObserver,
                () -> {
                    requireContext(request.hasContext());

                    var secured =
                            requestSecurity.authorizeTenantAdministration(
                                    request.getContext(),
                                    IdentityGrpcAuthorities.PERMISSION_READ
                            );

                    int effectivePageSize =
                            pageSize(request.getPage().getPageSize());

                    var page = catalogService.listPermissions(
                            effectivePageSize,
                            request.getPage().getPageToken(),
                            request.getResourceType()
                    );

                    log.debug(
                            "Permissions listed successfully: "
                                    + "tenantId={}, actorId={}, pageSize={}, "
                                    + "resourceType={}, correlationId={}",
                            secured.tenantId(),
                            secured.actorId(),
                            effectivePageSize,
                            request.getResourceType(),
                            request.getContext().getCorrelationId()
                    );

                    return protoMapper.toListPermissionsResponse(page);
                }
        );
    }

    @Override
    public void listUsers(
            ListUsersRequest request,
            StreamObserver<ListUsersResponse> responseObserver
    ) {
        log.debug(
                "Received list tenant users request: "
                        + "requestedPageSize={}, membershipStatus={}, "
                        + "hasRoleFilter={}, hasSearchFilter={}",
                request.getPage().getPageSize(),
                request.getMembershipStatus(),
                !request.getRoleCode().isBlank(),
                !request.getSearchText().isBlank()
        );

        exceptionMapper.executeUnary(
                responseObserver,
                () -> {
                    requireContext(request.hasContext());

                    var secured =
                            requestSecurity.authorizeTenantAdministration(
                                    request.getContext(),
                                    IdentityGrpcAuthorities.USER_READ
                            );

                    int effectivePageSize =
                            pageSize(request.getPage().getPageSize());

                    var page = administrationService.listUsers(
                            secured.tenantId(),
                            effectivePageSize,
                            request.getPage().getPageToken(),
                            request.getSearchText(),
                            protoMapper.toDomainStatus(
                                    request.getMembershipStatus()
                            ),
                            request.getRoleCode()
                    );

                    log.debug(
                            "Tenant users listed successfully: "
                                    + "tenantId={}, actorId={}, pageSize={}, "
                                    + "membershipStatus={}, correlationId={}",
                            secured.tenantId(),
                            secured.actorId(),
                            effectivePageSize,
                            request.getMembershipStatus(),
                            request.getContext().getCorrelationId()
                    );

                    return protoMapper.toListUsersResponse(page);
                }
        );
    }

    @Override
    public void getUserAuthorization(
            GetUserAuthorizationRequest request,
            StreamObserver<GetUserAuthorizationResponse> responseObserver
    ) {
        log.debug(
                "Received get user authorization request: subjectId={}",
                request.getSubjectId()
        );

        exceptionMapper.executeUnary(
                responseObserver,
                () -> {
                    requireContext(request.hasContext());
                    requireText(request.getSubjectId(), "subject_id");

                    var secured =
                            requestSecurity.authorizeTenantAdministration(
                                    request.getContext(),
                                    IdentityGrpcAuthorities.USER_READ
                            );

                    var authorization =
                            administrationService.getUserAuthorization(
                                    secured.tenantId(),
                                    request.getSubjectId()
                            );

                    log.debug(
                            "User authorization retrieved successfully: "
                                    + "tenantId={}, actorId={}, subjectId={}, "
                                    + "correlationId={}",
                            secured.tenantId(),
                            secured.actorId(),
                            request.getSubjectId(),
                            request.getContext().getCorrelationId()
                    );

                    return GetUserAuthorizationResponse.newBuilder()
                            .setAuthorization(
                                    protoMapper.toProto(authorization)
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
        log.info(
                "Received create tenant user request: "
                        + "initialRoleCount={}, activateImmediately={}, "
                        + "hasDisplayName={}, hasPhoneNumber={}",
                request.getInitialRoleCodesCount(),
                request.getActivateImmediately(),
                !request.getDisplayName().isBlank(),
                !request.getPhoneNumber().isBlank()
        );

        exceptionMapper.executeUnary(
                responseObserver,
                () -> {
                    requireContext(request.hasContext());
                    validateCreateUser(request);

                    var secured =
                            requestSecurity.authorizeTenantAdministration(
                                    request.getContext(),
                                    IdentityGrpcAuthorities.USER_CREATE
                            );

                    List<String> roles =
                            request.getInitialRoleCodesList().isEmpty()
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

                    log.info(
                            "Tenant user created successfully: "
                                    + "tenantId={}, actorId={}, initialRoleCount={}, "
                                    + "activateImmediately={}, correlationId={}",
                            secured.tenantId(),
                            secured.actorId(),
                            roles.size(),
                            request.getActivateImmediately(),
                            request.getContext().getCorrelationId()
                    );

                    return protoMapper.toCreateUserResponse(result);
                }
        );
    }

    @Override
    public void createTenantRole(
            CreateTenantRoleRequest r,
            StreamObserver<RoleMutationResponse> o
    ) {
        log.info(
                "Received create tenant role request: roleCode={}, "
                        + "hasRoleName={}",
                r.getCode(),
                !r.getName().isBlank()
        );

        exceptionMapper.executeUnary(
                o,
                () -> {
                    requireContext(r.hasContext());

                    var s = requestSecurity.authorizeTenantAdministration(
                            r.getContext(),
                            IdentityGrpcAuthorities.ROLE_MANAGE
                    );

                    dynamicAuthorization.createRole(
                            s.tenantId(),
                            r.getCode(),
                            r.getName(),
                            s.actorId(),
                            r.getContext().getCorrelationId()
                    );

                    var role = administrationService.getRole(
                            s.tenantId(),
                            null,
                            r.getCode()
                    );

                    log.info(
                            "Tenant role created successfully: "
                                    + "tenantId={}, actorId={}, roleCode={}, "
                                    + "correlationId={}",
                            s.tenantId(),
                            s.actorId(),
                            r.getCode(),
                            r.getContext().getCorrelationId()
                    );

                    return RoleMutationResponse.newBuilder()
                            .setRole(protoMapper.toProto(role))
                            .build();
                }
        );
    }

    @Override
    public void updateTenantRole(
            UpdateTenantRoleRequest r,
            StreamObserver<RoleMutationResponse> o
    ) {
        log.info(
                "Received update tenant role request: "
                        + "roleCode={}, hasRoleName={}",
                r.getCode(),
                !r.getName().isBlank()
        );

        exceptionMapper.executeUnary(
                o,
                () -> {
                    requireContext(r.hasContext());

                    var s = requestSecurity.authorizeTenantAdministration(
                            r.getContext(),
                            IdentityGrpcAuthorities.ROLE_MANAGE
                    );

                    dynamicAuthorization.updateRole(
                            s.tenantId(),
                            r.getCode(),
                            r.getName()
                    );

                    var role = administrationService.getRole(
                            s.tenantId(),
                            null,
                            r.getCode()
                    );

                    log.info(
                            "Tenant role updated successfully: "
                                    + "tenantId={}, actorId={}, roleCode={}, "
                                    + "correlationId={}",
                            s.tenantId(),
                            s.actorId(),
                            r.getCode(),
                            r.getContext().getCorrelationId()
                    );

                    return RoleMutationResponse.newBuilder()
                            .setRole(protoMapper.toProto(role))
                            .build();
                }
        );
    }

    @Override
    public void deleteTenantRole(
            DeleteTenantRoleRequest r,
            StreamObserver<DeleteAuthorizationObjectResponse> o
    ) {
        log.info(
                "Received delete tenant role request: roleCode={}",
                r.getCode()
        );

        exceptionMapper.executeUnary(
                o,
                () -> {
                    requireContext(r.hasContext());

                    var s = requestSecurity.authorizeTenantAdministration(
                            r.getContext(),
                            IdentityGrpcAuthorities.ROLE_MANAGE
                    );

                    boolean deleted = dynamicAuthorization.deleteRole(
                            s.tenantId(),
                            r.getCode()
                    );

                    log.info(
                            "Tenant role deletion completed: "
                                    + "tenantId={}, actorId={}, roleCode={}, "
                                    + "deleted={}, correlationId={}",
                            s.tenantId(),
                            s.actorId(),
                            r.getCode(),
                            deleted,
                            r.getContext().getCorrelationId()
                    );

                    return DeleteAuthorizationObjectResponse.newBuilder()
                            .setDeleted(deleted)
                            .build();
                }
        );
    }

    @Override
    public void createPermission(
            CreatePermissionRequest request,
            StreamObserver<PermissionMutationResponse> observer
    ) {
        observer.onError(
                io.grpc.Status.FAILED_PRECONDITION
                        .withDescription(
                                "PERMISSION_CATALOG_PLATFORM_OWNED: use "
                                        + "PlatformAuthorizationAdministrationService"
                        )
                        .asRuntimeException()
        );
    }

    @Override
    public void updatePermission(
            UpdatePermissionRequest request,
            StreamObserver<PermissionMutationResponse> observer
    ) {
        observer.onError(
                io.grpc.Status.FAILED_PRECONDITION
                        .withDescription(
                                "PERMISSION_CATALOG_PLATFORM_OWNED: use "
                                        + "PlatformAuthorizationAdministrationService"
                        )
                        .asRuntimeException()
        );
    }

    @Override
    public void deletePermission(
            DeletePermissionRequest request,
            StreamObserver<DeleteAuthorizationObjectResponse> observer
    ) {
        observer.onError(
                io.grpc.Status.FAILED_PRECONDITION
                        .withDescription(
                                "PERMISSION_CATALOG_PLATFORM_OWNED: use "
                                        + "PlatformAuthorizationAdministrationService"
                        )
                        .asRuntimeException()
        );
    }

    @Override
    public void grantPermissionToRole(
            TenantRolePermissionRequest r,
            StreamObserver<RoleMutationResponse> o
    ) {
        log.info(
                "Received grant permission to role request: "
                        + "roleCode={}, permissionCode={}",
                r.getRoleCode(),
                r.getPermissionCode()
        );

        exceptionMapper.executeUnary(
                o,
                () -> {
                    requireContext(r.hasContext());

                    var s = requestSecurity.authorizeTenantAdministration(
                            r.getContext(),
                            IdentityGrpcAuthorities.PERMISSION_MANAGE
                    );

                    dynamicAuthorization.grant(
                            s.tenantId(),
                            r.getRoleCode(),
                            r.getPermissionCode(),
                            s.actorId(),
                            r.getContext().getCorrelationId()
                    );

                    var role = administrationService.getRole(
                            s.tenantId(),
                            null,
                            r.getRoleCode()
                    );

                    log.info(
                            "Permission granted to role successfully: "
                                    + "tenantId={}, actorId={}, roleCode={}, "
                                    + "permissionCode={}, correlationId={}",
                            s.tenantId(),
                            s.actorId(),
                            r.getRoleCode(),
                            r.getPermissionCode(),
                            r.getContext().getCorrelationId()
                    );

                    return RoleMutationResponse.newBuilder()
                            .setRole(protoMapper.toProto(role))
                            .build();
                }
        );
    }

    @Override
    public void revokePermissionFromRole(
            TenantRolePermissionRequest r,
            StreamObserver<RoleMutationResponse> o
    ) {
        log.info(
                "Received revoke permission from role request: "
                        + "roleCode={}, permissionCode={}",
                r.getRoleCode(),
                r.getPermissionCode()
        );

        exceptionMapper.executeUnary(
                o,
                () -> {
                    requireContext(r.hasContext());

                    var s = requestSecurity.authorizeTenantAdministration(
                            r.getContext(),
                            IdentityGrpcAuthorities.PERMISSION_MANAGE
                    );

                    dynamicAuthorization.revoke(
                            s.tenantId(),
                            r.getRoleCode(),
                            r.getPermissionCode(),
                            s.actorId(),
                            r.getContext().getCorrelationId()
                    );

                    var role = administrationService.getRole(
                            s.tenantId(),
                            null,
                            r.getRoleCode()
                    );

                    log.info(
                            "Permission revoked from role successfully: "
                                    + "tenantId={}, actorId={}, roleCode={}, "
                                    + "permissionCode={}, correlationId={}",
                            s.tenantId(),
                            s.actorId(),
                            r.getRoleCode(),
                            r.getPermissionCode(),
                            r.getContext().getCorrelationId()
                    );

                    return RoleMutationResponse.newBuilder()
                            .setRole(protoMapper.toProto(role))
                            .build();
                }
        );
    }

    @Override
    public void updateUserProfileAsAdministrator(
            AdminUpdateUserProfileRequest request,
            StreamObserver<AdminUserPrincipalResponse> observer
    ) {
        log.info(
                "Received administrator user profile update request: "
                        + "subjectId={}, hasExpectedVersion={}",
                request.getSubjectId(),
                request.hasExpectedVersion()
        );

        exceptionMapper.executeUnary(
                observer,
                () -> {
                    requireContext(request.hasContext());
                    requireText(request.getSubjectId(), "subject_id");

                    var secured =
                            requestSecurity.authorizeTenantAdministration(
                                    request.getContext(),
                                    IdentityGrpcAuthorities.USER_PROFILE_MANAGE
                            );

                    var legacy = UpdatePrincipalProfileRequest.newBuilder()
                            .setContext(request.getContext())
                            .setSubjectId(request.getSubjectId())
                            .setProfile(request.getProfile())
                            .setUpdateMask(request.getUpdateMask());

                    if (request.hasExpectedVersion()) {
                        legacy.setExpectedVersion(
                                request.getExpectedVersion()
                        );
                    }

                    var command = identityProtoMapper.toUpdateProfileCommand(
                            request.getSubjectId(),
                            legacy.build()
                    );

                    var principal = principalProfileService.updateProfile(
                            secured.tenantId(),
                            command,
                            secured.actorId(),
                            request.getContext().getCorrelationId()
                    );

                    log.info(
                            "User profile updated by administrator successfully: "
                                    + "tenantId={}, actorId={}, subjectId={}, "
                                    + "hasExpectedVersion={}, correlationId={}",
                            secured.tenantId(),
                            secured.actorId(),
                            request.getSubjectId(),
                            request.hasExpectedVersion(),
                            request.getContext().getCorrelationId()
                    );

                    return AdminUserPrincipalResponse.newBuilder()
                            .setPrincipal(
                                    identityProtoMapper.toProto(principal)
                            )
                            .build();
                }
        );
    }

    @Override
    public void disableUserAccount(
            AccountAdministrationRequest r,
            StreamObserver<GetUserAuthorizationResponse> o
    ) {
        log.info(
                "Received disable user account request: subjectId={}",
                r.getSubjectId()
        );

        accountAction(r, o, "disable");
    }

    @Override
    public void enableUserAccount(
            AccountAdministrationRequest r,
            StreamObserver<GetUserAuthorizationResponse> o
    ) {
        log.info(
                "Received enable user account request: subjectId={}",
                r.getSubjectId()
        );

        accountAction(r, o, "enable");
    }

    @Override
    public void unlockUserAccount(
            AccountAdministrationRequest r,
            StreamObserver<GetUserAuthorizationResponse> o
    ) {
        log.info(
                "Received unlock user account request: subjectId={}",
                r.getSubjectId()
        );

        accountAction(r, o, "unlock");
    }

    private void accountAction(
            AccountAdministrationRequest r,
            StreamObserver<GetUserAuthorizationResponse> o,
            String action
    ) {
        log.debug(
                "Processing account administration action: "
                        + "action={}, subjectId={}",
                action,
                r.getSubjectId()
        );

        exceptionMapper.executeUnary(
                o,
                () -> {
                    requireContext(r.hasContext());
                    requireText(r.getSubjectId(), "subject_id");

                    var secured =
                            requestSecurity.authorizeTenantAdministration(
                                    r.getContext(),
                                    IdentityGrpcAuthorities.USER_ACCOUNT_MANAGE
                            );

                    if (r.getSubjectId().equals(secured.actorId())
                            && "disable".equals(action)) {
                        log.warn(
                                "Account administration rejected: "
                                        + "cannot disable own account, tenantId={}, "
                                        + "actorId={}, subjectId={}, correlationId={}",
                                secured.tenantId(),
                                secured.actorId(),
                                r.getSubjectId(),
                                r.getContext().getCorrelationId()
                        );

                        throw new IllegalStateException(
                                "Cannot disable own account"
                        );
                    }

                    var value = switch (action) {
                        case "disable" ->
                                administrationService.disableAccount(
                                        secured.tenantId(),
                                        r.getSubjectId(),
                                        secured.actorId()
                                );
                        case "enable" ->
                                administrationService.enableAccount(
                                        secured.tenantId(),
                                        r.getSubjectId(),
                                        secured.actorId()
                                );
                        default ->
                                administrationService.unlockAccount(
                                        secured.tenantId(),
                                        r.getSubjectId(),
                                        secured.actorId()
                                );
                    };

                    log.info(
                            "Account administration action completed successfully: "
                                    + "tenantId={}, actorId={}, subjectId={}, "
                                    + "action={}, correlationId={}",
                            secured.tenantId(),
                            secured.actorId(),
                            r.getSubjectId(),
                            action,
                            r.getContext().getCorrelationId()
                    );

                    return GetUserAuthorizationResponse.newBuilder()
                            .setAuthorization(protoMapper.toProto(value))
                            .build();
                }
        );
    }

    @Override
    public void assignRole(
            AssignRoleRequest request,
            StreamObserver<AssignRoleResponse> responseObserver
    ) {
        log.info(
                "Received assign role request: "
                        + "subjectId={}, roleCode={}",
                request.getSubjectId(),
                request.getRoleCode()
        );

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
                            requestSecurity.authorizeTenantAdministration(
                                    request.getContext(),
                                    IdentityGrpcAuthorities.ROLE_ASSIGN
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

                    log.info(
                            "Role assigned successfully: tenantId={}, actorId={}, "
                                    + "subjectId={}, roleCode={}, correlationId={}",
                            secured.tenantId(),
                            secured.actorId(),
                            request.getSubjectId(),
                            request.getRoleCode(),
                            request.getContext().getCorrelationId()
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
        log.info(
                "Received revoke role request: "
                        + "subjectId={}, roleCode={}",
                request.getSubjectId(),
                request.getRoleCode()
        );

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
                            requestSecurity.authorizeTenantAdministration(
                                    request.getContext(),
                                    IdentityGrpcAuthorities.ROLE_ASSIGN
                            );

                    if (request.getSubjectId().equals(secured.actorId())
                            && "ADMIN".equals(request.getRoleCode())) {
                        log.warn(
                                "Role revocation rejected: "
                                        + "cannot remove own ADMIN role, tenantId={}, "
                                        + "actorId={}, subjectId={}, roleCode={}, "
                                        + "correlationId={}",
                                secured.tenantId(),
                                secured.actorId(),
                                request.getSubjectId(),
                                request.getRoleCode(),
                                request.getContext().getCorrelationId()
                        );

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

                    log.info(
                            "Role revoked successfully: tenantId={}, actorId={}, "
                                    + "subjectId={}, roleCode={}, correlationId={}",
                            secured.tenantId(),
                            secured.actorId(),
                            request.getSubjectId(),
                            request.getRoleCode(),
                            request.getContext().getCorrelationId()
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
            StreamObserver<UpdateMembershipStatusResponse> responseObserver
    ) {
        log.info(
                "Received disable membership request: "
                        + "subjectId={}, hasReason={}",
                request.getSubjectId(),
                !request.getReason().isBlank()
        );

        exceptionMapper.executeUnary(
                responseObserver,
                () -> {
                    requireContext(request.hasContext());
                    requireText(
                            request.getSubjectId(),
                            "subject_id"
                    );

                    var secured =
                            requestSecurity.authorizeTenantAdministration(
                                    request.getContext(),
                                    IdentityGrpcAuthorities.USER_DISABLE
                            );

                    if (request.getSubjectId().equals(secured.actorId())) {
                        log.warn(
                                "Disable membership rejected: "
                                        + "cannot disable own membership, tenantId={}, "
                                        + "actorId={}, subjectId={}, correlationId={}",
                                secured.tenantId(),
                                secured.actorId(),
                                request.getSubjectId(),
                                request.getContext().getCorrelationId()
                        );

                        throw new IllegalStateException(
                                "Cannot disable own membership"
                        );
                    }

                    var authorization =
                            administrationService.disableMembership(
                                    secured.tenantId(),
                                    request.getSubjectId(),
                                    secured.actorId(),
                                    request.getReason()
                            );

                    log.info(
                            "Membership disabled successfully: tenantId={}, "
                                    + "actorId={}, subjectId={}, correlationId={}",
                            secured.tenantId(),
                            secured.actorId(),
                            request.getSubjectId(),
                            request.getContext().getCorrelationId()
                    );

                    return protoMapper.toMembershipStatusResponse(
                            authorization
                    );
                }
        );
    }

    @Override
    public void enableMembership(
            EnableMembershipRequest request,
            StreamObserver<UpdateMembershipStatusResponse> responseObserver
    ) {
        log.info(
                "Received enable membership request: subjectId={}",
                request.getSubjectId()
        );

        exceptionMapper.executeUnary(
                responseObserver,
                () -> {
                    requireContext(request.hasContext());
                    requireText(
                            request.getSubjectId(),
                            "subject_id"
                    );

                    var secured =
                            requestSecurity.authorizeTenantAdministration(
                                    request.getContext(),
                                    IdentityGrpcAuthorities.USER_DISABLE
                            );

                    var authorization =
                            administrationService.enableMembership(
                                    secured.tenantId(),
                                    request.getSubjectId(),
                                    secured.actorId()
                            );

                    log.info(
                            "Membership enabled successfully: tenantId={}, "
                                    + "actorId={}, subjectId={}, correlationId={}",
                            secured.tenantId(),
                            secured.actorId(),
                            request.getSubjectId(),
                            request.getContext().getCorrelationId()
                    );

                    return protoMapper.toMembershipStatusResponse(
                            authorization
                    );
                }
        );
    }

    @Override
    public void suspendMembership(
            SuspendMembershipRequest request,
            StreamObserver<UpdateMembershipStatusResponse> responseObserver
    ) {
        log.info(
                "Received suspend membership request: "
                        + "subjectId={}, hasReason={}",
                request.getSubjectId(),
                !request.getReason().isBlank()
        );

        exceptionMapper.executeUnary(
                responseObserver,
                () -> {
                    requireContext(request.hasContext());
                    requireText(
                            request.getSubjectId(),
                            "subject_id"
                    );

                    var secured =
                            requestSecurity.authorizeTenantAdministration(
                                    request.getContext(),
                                    IdentityGrpcAuthorities.USER_DISABLE
                            );

                    if (request.getSubjectId().equals(secured.actorId())) {
                        log.warn(
                                "Suspend membership rejected: "
                                        + "cannot suspend own membership, tenantId={}, "
                                        + "actorId={}, subjectId={}, correlationId={}",
                                secured.tenantId(),
                                secured.actorId(),
                                request.getSubjectId(),
                                request.getContext().getCorrelationId()
                        );

                        throw new IllegalStateException(
                                "Cannot suspend own membership"
                        );
                    }

                    var authorization =
                            administrationService.suspendMembership(
                                    secured.tenantId(),
                                    request.getSubjectId(),
                                    secured.actorId(),
                                    request.getReason()
                            );

                    log.info(
                            "Membership suspended successfully: tenantId={}, "
                                    + "actorId={}, subjectId={}, correlationId={}",
                            secured.tenantId(),
                            secured.actorId(),
                            request.getSubjectId(),
                            request.getContext().getCorrelationId()
                    );

                    return protoMapper.toMembershipStatusResponse(
                            authorization
                    );
                }
        );
    }

    private int pageSize(int requestedPageSize) {
        if (requestedPageSize <= 0) {
            log.debug(
                    "Using default page size: requestedPageSize={}, "
                            + "effectivePageSize={}",
                    requestedPageSize,
                    DEFAULT_PAGE_SIZE
            );

            return DEFAULT_PAGE_SIZE;
        }

        int effectivePageSize = Math.min(
                requestedPageSize,
                MAX_PAGE_SIZE
        );

        if (requestedPageSize > MAX_PAGE_SIZE) {
            log.debug(
                    "Requested page size exceeds maximum and was limited: "
                            + "requestedPageSize={}, effectivePageSize={}",
                    requestedPageSize,
                    effectivePageSize
            );
        }

        return effectivePageSize;
    }

    private void validateCreateUser(
            CreateUserRequest request
    ) {
        log.debug(
                "Validating create user request: "
                        + "hasEmail={}, passwordLength={}, initialRoleCount={}",
                !request.getEmail().isBlank(),
                request.getInitialPassword().length(),
                request.getInitialRoleCodesCount()
        );

        requireText(request.getEmail(), "email");
        requireText(
                request.getInitialPassword(),
                "initial_password"
        );

        if (request.getInitialPassword().length()
                < PasswordPolicy.MINIMUM_CHARACTERS) {
            log.warn(
                    "Create user validation failed: "
                            + "initial password is shorter than minimum length, "
                            + "passwordLength={}",
                    request.getInitialPassword().length()
            );

            throw new IllegalArgumentException(
                    "initial_password must contain at least "
                            + PasswordPolicy.MINIMUM_CHARACTERS
                            + " characters"
            );
        }

        int passwordUtf8Bytes = PasswordPolicy.utf8Length(
                request.getInitialPassword()
        );
        if (passwordUtf8Bytes > PasswordPolicy.MAXIMUM_UTF8_BYTES) {
            log.warn(
                    "Create user validation failed: "
                            + "initial password exceeds maximum length, "
                            + "passwordUtf8Bytes={}",
                    passwordUtf8Bytes
            );

            throw new IllegalArgumentException(
                    "initial_password must not exceed "
                            + PasswordPolicy.MAXIMUM_UTF8_BYTES
                            + " UTF-8 bytes"
            );
        }

        log.debug("Create user request validation completed successfully");
    }

    private void validateTenantRole(String roleCode) {
        requireText(roleCode, "role_code");

        String normalized = roleCode.trim().toUpperCase(Locale.ROOT);

        if ("PLATFORM_ADMIN".equals(normalized)
                || "SUPERADMIN".equals(normalized)) {
            log.warn(
                    "Tenant role validation failed: "
                            + "platform role delegation is not allowed, roleCode={}",
                    normalized
            );

            throw new IllegalArgumentException(
                    "Platform roles cannot be delegated "
                            + "through tenant administration"
            );
        }

        log.debug(
                "Tenant role validation completed successfully: roleCode={}",
                normalized
        );
    }

    private void requireContext(boolean hasContext) {
        if (!hasContext) {
            log.warn(
                    "Request validation failed: request context is missing"
            );

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
            log.warn(
                    "Request validation failed: required field is blank, field={}",
                    field
            );

            throw new IllegalArgumentException(
                    field + " must not be blank"
            );
        }
    }
}