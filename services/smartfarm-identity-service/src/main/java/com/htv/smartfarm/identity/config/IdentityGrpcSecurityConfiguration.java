package com.htv.smartfarm.identity.config;

import com.htv.smartfarm.identity.grpc.security.IdentityGrpcAuthorities;
import com.htv.smartfarm.proto.identity.v1.IdentityAdministrationServiceGrpc;
import com.htv.smartfarm.proto.identity.v1.IdentityDirectoryServiceGrpc;
import com.htv.smartfarm.proto.identity.v1.IdentityCredentialServiceGrpc;
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

                // Credential self-service and credential administration
                .requireAuthority(IdentityCredentialServiceGrpc.getGetSecurityProfileMethod().getFullMethodName(), IdentityGrpcAuthorities.SECURITY_READ)
                .requireAuthority(IdentityCredentialServiceGrpc.getBeginTotpEnrollmentMethod().getFullMethodName(), IdentityGrpcAuthorities.MFA_ENROLL)
                .requireAuthority(IdentityCredentialServiceGrpc.getConfirmTotpEnrollmentMethod().getFullMethodName(), IdentityGrpcAuthorities.MFA_ENROLL)
                .requireAuthority(IdentityCredentialServiceGrpc.getDisableOwnMfaMethod().getFullMethodName(), IdentityGrpcAuthorities.MFA_DISABLE)
                .requireAuthority(IdentityCredentialServiceGrpc.getRegenerateRecoveryCodesMethod().getFullMethodName(), IdentityGrpcAuthorities.MFA_RECOVERY_REGENERATE)
                .requireAuthority(IdentityCredentialServiceGrpc.getResetUserMfaMethod().getFullMethodName(), IdentityGrpcAuthorities.USER_MFA_RESET)
                .requireAuthority(IdentityCredentialServiceGrpc.getChangeOwnPasswordMethod().getFullMethodName(), IdentityGrpcAuthorities.USER_CREDENTIAL_RESET)

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

                // Dynamic tenant authorization administration
                .requireAuthority(IdentityAdministrationServiceGrpc.getCreateTenantRoleMethod().getFullMethodName(), IdentityGrpcAuthorities.ROLE_MANAGE)
                .requireAuthority(IdentityAdministrationServiceGrpc.getUpdateTenantRoleMethod().getFullMethodName(), IdentityGrpcAuthorities.ROLE_MANAGE)
                .requireAuthority(IdentityAdministrationServiceGrpc.getDeleteTenantRoleMethod().getFullMethodName(), IdentityGrpcAuthorities.ROLE_MANAGE)
                .requireAuthority(IdentityAdministrationServiceGrpc.getCreatePermissionMethod().getFullMethodName(), IdentityGrpcAuthorities.PERMISSION_MANAGE)
                .requireAuthority(IdentityAdministrationServiceGrpc.getUpdatePermissionMethod().getFullMethodName(), IdentityGrpcAuthorities.PERMISSION_MANAGE)
                .requireAuthority(IdentityAdministrationServiceGrpc.getDeletePermissionMethod().getFullMethodName(), IdentityGrpcAuthorities.PERMISSION_MANAGE)
                .requireAuthority(IdentityAdministrationServiceGrpc.getGrantPermissionToRoleMethod().getFullMethodName(), IdentityGrpcAuthorities.PERMISSION_MANAGE)
                .requireAuthority(IdentityAdministrationServiceGrpc.getRevokePermissionFromRoleMethod().getFullMethodName(), IdentityGrpcAuthorities.PERMISSION_MANAGE)

                // SUPERADMIN profile/account administration
                .requireAuthority(IdentityAdministrationServiceGrpc.getUpdateUserProfileAsAdministratorMethod().getFullMethodName(), IdentityGrpcAuthorities.USER_PROFILE_MANAGE)
                .requireAuthority(IdentityAdministrationServiceGrpc.getDisableUserAccountMethod().getFullMethodName(), IdentityGrpcAuthorities.USER_ACCOUNT_MANAGE)
                .requireAuthority(IdentityAdministrationServiceGrpc.getEnableUserAccountMethod().getFullMethodName(), IdentityGrpcAuthorities.USER_ACCOUNT_MANAGE)
                .requireAuthority(IdentityAdministrationServiceGrpc.getUnlockUserAccountMethod().getFullMethodName(), IdentityGrpcAuthorities.USER_ACCOUNT_MANAGE)

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