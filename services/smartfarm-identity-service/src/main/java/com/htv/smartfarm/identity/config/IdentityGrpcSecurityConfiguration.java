package com.htv.smartfarm.identity.config;

import com.htv.smartfarm.identity.grpc.security.IdentityGrpcAuthorities;
import com.htv.smartfarm.proto.identity.v1.IdentityAdministrationServiceGrpc;
import com.htv.smartfarm.proto.identity.v1.IdentityDirectoryServiceGrpc;
import com.htv.smartfarm.proto.identity.v1.PlatformAuthorizationAdministrationServiceGrpc;
import com.htv.smartfarm.security.grpc.GrpcMethodPolicy;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class IdentityGrpcSecurityConfiguration {

    @Bean
    GrpcMethodPolicy identityGrpcMethodPolicy() {
        return GrpcMethodPolicy.builder()

                // Directory
                .requireAuthority(
                        IdentityDirectoryServiceGrpc
                                .getGetPrincipalMethod()
                                .getFullMethodName(),
                        IdentityGrpcAuthorities.PRINCIPAL_READ
                )
                .requireAuthority(
                        IdentityDirectoryServiceGrpc
                                .getUpdatePrincipalProfileMethod()
                                .getFullMethodName(),
                        IdentityGrpcAuthorities.PRINCIPAL_UPDATE
                )
                .requireAuthority(
                        IdentityDirectoryServiceGrpc
                                .getCheckPermissionMethod()
                                .getFullMethodName(),
                        IdentityGrpcAuthorities.PERMISSION_CHECK
                )
                .requireAuthority(
                        IdentityDirectoryServiceGrpc
                                .getBatchCheckPermissionsMethod()
                                .getFullMethodName(),
                        IdentityGrpcAuthorities.PERMISSION_CHECK
                )
                .requireAuthenticated(
                        IdentityDirectoryServiceGrpc
                                .getPingMethod()
                                .getFullMethodName()
                )

                // Tenant role catalog
                .requireAuthority(
                        IdentityAdministrationServiceGrpc
                                .getListRolesMethod()
                                .getFullMethodName(),
                        IdentityGrpcAuthorities.ROLE_READ
                )
                .requireAuthority(
                        IdentityAdministrationServiceGrpc
                                .getGetRoleMethod()
                                .getFullMethodName(),
                        IdentityGrpcAuthorities.ROLE_READ
                )

                // Permission catalog
                .requireAuthority(
                        IdentityAdministrationServiceGrpc
                                .getListPermissionsMethod()
                                .getFullMethodName(),
                        IdentityGrpcAuthorities.PERMISSION_READ
                )

                // Tenant users
                .requireAuthority(
                        IdentityAdministrationServiceGrpc
                                .getListUsersMethod()
                                .getFullMethodName(),
                        IdentityGrpcAuthorities.USER_READ
                )
                .requireAuthority(
                        IdentityAdministrationServiceGrpc
                                .getGetUserAuthorizationMethod()
                                .getFullMethodName(),
                        IdentityGrpcAuthorities.USER_READ
                )
                .requireAuthority(
                        IdentityAdministrationServiceGrpc
                                .getCreateUserMethod()
                                .getFullMethodName(),
                        IdentityGrpcAuthorities.USER_CREATE
                )

                // Role assignment
                .requireAuthority(
                        IdentityAdministrationServiceGrpc
                                .getAssignRoleMethod()
                                .getFullMethodName(),
                        IdentityGrpcAuthorities.ROLE_ASSIGN
                )
                .requireAuthority(
                        IdentityAdministrationServiceGrpc
                                .getRevokeRoleMethod()
                                .getFullMethodName(),
                        IdentityGrpcAuthorities.ROLE_ASSIGN
                )

                // Membership status
                .requireAuthority(
                        IdentityAdministrationServiceGrpc
                                .getDisableMembershipMethod()
                                .getFullMethodName(),
                        IdentityGrpcAuthorities.USER_DISABLE
                )
                .requireAuthority(
                        IdentityAdministrationServiceGrpc
                                .getEnableMembershipMethod()
                                .getFullMethodName(),
                        IdentityGrpcAuthorities.USER_DISABLE
                )
                .requireAuthority(
                        IdentityAdministrationServiceGrpc
                                .getSuspendMembershipMethod()
                                .getFullMethodName(),
                        IdentityGrpcAuthorities.USER_DISABLE
                )

                // Platform
                .requireAuthority(
                        PlatformAuthorizationAdministrationServiceGrpc
                                .getListPlatformRolesMethod()
                                .getFullMethodName(),
                        IdentityGrpcAuthorities.PLATFORM_ROLE_MANAGE
                )
                .requireAuthority(
                        PlatformAuthorizationAdministrationServiceGrpc
                                .getCreatePlatformRoleMethod()
                                .getFullMethodName(),
                        IdentityGrpcAuthorities.PLATFORM_ROLE_MANAGE
                )
                .requireAuthority(
                        PlatformAuthorizationAdministrationServiceGrpc
                                .getGrantPlatformPermissionMethod()
                                .getFullMethodName(),
                        IdentityGrpcAuthorities
                                .PLATFORM_PERMISSION_MANAGE
                )
                .requireAuthority(
                        PlatformAuthorizationAdministrationServiceGrpc
                                .getRevokePlatformPermissionMethod()
                                .getFullMethodName(),
                        IdentityGrpcAuthorities
                                .PLATFORM_PERMISSION_MANAGE
                )
                .build();
    }
}