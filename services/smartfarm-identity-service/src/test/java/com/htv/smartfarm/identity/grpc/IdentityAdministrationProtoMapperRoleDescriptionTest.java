package com.htv.smartfarm.identity.grpc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.htv.smartfarm.identity.administration.application.model.RoleData;
import com.htv.smartfarm.identity.administration.application.model.RoleTypeData;
import com.htv.smartfarm.proto.identity.v1.IdentityProto;

import org.junit.jupiter.api.Test;

class IdentityAdministrationProtoMapperRoleDescriptionTest {

    @Test
    void mapsRoleDescriptionAndKeepsTheV1FieldNumber() {
        var role = new RoleData(
                "role-id",
                "tenant-id",
                "OPERATOR",
                "Operator",
                "Can manage daily farm operations",
                RoleTypeData.PLATFORM,
                List.of(),
                null,
                null,
                0
        );

        var mapped = new IdentityAdministrationProtoMapper().toProto(role);

        assertThat(mapped.getDescription())
                .isEqualTo("Can manage daily farm operations");
        Map.of(
                "role_id", 1,
                "tenant_id", 2,
                "code", 3,
                "name", 4,
                "type", 5,
                "permissions", 6,
                "created_at", 7,
                "updated_at", 8,
                "version", 9,
                "description", 10
        ).forEach((fieldName, expectedNumber) ->
                assertThat(mapped.getDescriptorForType()
                        .findFieldByName(fieldName)
                        .getNumber())
                        .as("RoleInfo.%s field number", fieldName)
                        .isEqualTo(expectedNumber)
        );
    }

    @Test
    void keepsExistingIdentityGrpcServicesAndMethodsAvailable() {
        Map<String, Set<String>> requiredMethods = Map.of(
                "IdentityDirectoryService",
                Set.of(
                        "GetPrincipal",
                        "UpdatePrincipalProfile",
                        "CheckPermission",
                        "BatchCheckPermissions",
                        "Ping"
                ),
                "IdentityAdministrationService",
                Set.of(
                        "ListRoles",
                        "GetRole",
                        "ListPermissions",
                        "ListUsers",
                        "GetUserAuthorization",
                        "CreateUser",
                        "AssignRole",
                        "RevokeRole",
                        "DisableMembership",
                        "EnableMembership",
                        "SuspendMembership",
                        "CreateTenantRole",
                        "UpdateTenantRole",
                        "DeleteTenantRole",
                        "CreatePermission",
                        "UpdatePermission",
                        "DeletePermission",
                        "GrantPermissionToRole",
                        "UpdateUserProfileAsAdministrator",
                        "DisableUserAccount",
                        "EnableUserAccount",
                        "UnlockUserAccount",
                        "RevokePermissionFromRole"
                ),
                "PlatformAuthorizationAdministrationService",
                Set.of(
                        "ListPlatformRoles",
                        "CreatePlatformRole",
                        "GrantPlatformPermission",
                        "RevokePlatformPermission",
                        "GetGlobalPermission",
                        "CreateGlobalPermission",
                        "UpdateGlobalPermission",
                        "DeleteGlobalPermission"
                ),
                "IdentityCredentialService",
                Set.of(
                        "GetSecurityProfile",
                        "BeginTotpEnrollment",
                        "ConfirmTotpEnrollment",
                        "DisableOwnMfa",
                        "RegenerateRecoveryCodes",
                        "ResetUserMfa",
                        "ChangeOwnPassword"
                ),
                "IdentityTokenService",
                Set.of(
                        "IssueServiceToken",
                        "ExchangeDelegatedToken",
                        "ListSessions",
                        "RevokeSession",
                        "RevokeAllSessions"
                )
        );

        requiredMethods.forEach((serviceName, methods) -> {
            var service = IdentityProto.getDescriptor()
                    .findServiceByName(serviceName);

            assertThat(service)
                    .as("Identity v1 service %s", serviceName)
                    .isNotNull();
            assertThat(service.getMethods())
                    .extracting(com.google.protobuf.Descriptors.MethodDescriptor::getName)
                    .containsAll(methods);
        });
    }
}
