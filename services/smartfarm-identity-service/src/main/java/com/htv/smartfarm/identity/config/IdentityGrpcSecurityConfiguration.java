package com.htv.smartfarm.identity.config;

import com.htv.smartfarm.identity.grpc.security.IdentityGrpcAuthorities;
import com.htv.smartfarm.proto.identity.v1.IdentityAdministrationServiceGrpc;
import com.htv.smartfarm.proto.identity.v1.IdentityCredentialServiceGrpc;
import com.htv.smartfarm.proto.identity.v1.IdentityDirectoryServiceGrpc;
import com.htv.smartfarm.proto.identity.v1.IdentityTokenServiceGrpc;
import com.htv.smartfarm.proto.identity.v1.PlatformAuthorizationAdministrationServiceGrpc;
import com.htv.smartfarm.security.grpc.GrpcMethodPolicy;

import io.grpc.MethodDescriptor;

import lombok.extern.slf4j.Slf4j;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration(proxyBeanMethods = false)
public class IdentityGrpcSecurityConfiguration {

    @Bean
    GrpcMethodPolicy identityGrpcMethodPolicy() {
        log.info("Initializing Identity gRPC method security policy");

        var builder = GrpcMethodPolicy.builder();

        configureDirectoryPolicy(builder);
        configureCredentialPolicy(builder);
        configureTenantCatalogPolicy(builder);
        configureTenantUserPolicy(builder);
        configureTenantAuthorizationPolicy(builder);
        configureUserAdministrationPolicy(builder);
        configureMembershipPolicy(builder);
        configureTokenPolicy(builder);
        configurePlatformPolicy(builder);

        var policy = builder.build();

        log.info(
                "Identity gRPC method security policy initialized successfully"
        );

        return policy;
    }

    /**
     * Configures principal directory operations and permission checks.
     */
    private void configureDirectoryPolicy(
            GrpcMethodPolicy.Builder builder
    ) {
        requireAuthority(
                builder,
                IdentityDirectoryServiceGrpc.getGetPrincipalMethod(),
                IdentityGrpcAuthorities.PRINCIPAL_READ
        );

        requireAuthority(
                builder,
                IdentityDirectoryServiceGrpc
                        .getUpdatePrincipalProfileMethod(),
                IdentityGrpcAuthorities.PRINCIPAL_UPDATE
        );

        requireAuthority(
                builder,
                IdentityDirectoryServiceGrpc.getCheckPermissionMethod(),
                IdentityGrpcAuthorities.PERMISSION_CHECK
        );

        requireAuthority(
                builder,
                IdentityDirectoryServiceGrpc
                        .getBatchCheckPermissionsMethod(),
                IdentityGrpcAuthorities.PERMISSION_CHECK
        );

        requireAuthenticated(
                builder,
                IdentityDirectoryServiceGrpc.getPingMethod()
        );
    }

    /**
     * Configures credential self-service and credential administration.
     */
    private void configureCredentialPolicy(
            GrpcMethodPolicy.Builder builder
    ) {
        requireAuthority(
                builder,
                IdentityCredentialServiceGrpc
                        .getGetSecurityProfileMethod(),
                IdentityGrpcAuthorities.SECURITY_READ
        );

        requireAuthority(
                builder,
                IdentityCredentialServiceGrpc
                        .getBeginTotpEnrollmentMethod(),
                IdentityGrpcAuthorities.MFA_ENROLL
        );

        requireAuthority(
                builder,
                IdentityCredentialServiceGrpc
                        .getConfirmTotpEnrollmentMethod(),
                IdentityGrpcAuthorities.MFA_ENROLL
        );

        requireAuthority(
                builder,
                IdentityCredentialServiceGrpc.getDisableOwnMfaMethod(),
                IdentityGrpcAuthorities.MFA_DISABLE
        );

        requireAuthority(
                builder,
                IdentityCredentialServiceGrpc
                        .getRegenerateRecoveryCodesMethod(),
                IdentityGrpcAuthorities.MFA_RECOVERY_REGENERATE
        );

        requireAuthority(
                builder,
                IdentityCredentialServiceGrpc.getResetUserMfaMethod(),
                IdentityGrpcAuthorities.USER_MFA_RESET
        );

        requireAuthority(
                builder,
                IdentityCredentialServiceGrpc
                        .getChangeOwnPasswordMethod(),
                IdentityGrpcAuthorities.USER_CREDENTIAL_RESET
        );
    }

    /**
     * Configures read operations for tenant role and permission catalogs.
     */
    private void configureTenantCatalogPolicy(
            GrpcMethodPolicy.Builder builder
    ) {
        requireAuthority(
                builder,
                IdentityAdministrationServiceGrpc.getListRolesMethod(),
                IdentityGrpcAuthorities.ROLE_READ
        );

        requireAuthority(
                builder,
                IdentityAdministrationServiceGrpc.getGetRoleMethod(),
                IdentityGrpcAuthorities.ROLE_READ
        );

        requireAuthority(
                builder,
                IdentityAdministrationServiceGrpc
                        .getListPermissionsMethod(),
                IdentityGrpcAuthorities.PERMISSION_READ
        );
    }

    /**
     * Configures tenant user query and creation operations.
     */
    private void configureTenantUserPolicy(
            GrpcMethodPolicy.Builder builder
    ) {
        requireAuthority(
                builder,
                IdentityAdministrationServiceGrpc.getListUsersMethod(),
                IdentityGrpcAuthorities.USER_READ
        );

        requireAuthority(
                builder,
                IdentityAdministrationServiceGrpc
                        .getGetUserAuthorizationMethod(),
                IdentityGrpcAuthorities.USER_READ
        );

        requireAuthority(
                builder,
                IdentityAdministrationServiceGrpc.getCreateUserMethod(),
                IdentityGrpcAuthorities.USER_CREATE
        );
    }

    /**
     * Configures dynamic tenant roles, permissions, grants and revocations.
     */
    private void configureTenantAuthorizationPolicy(
            GrpcMethodPolicy.Builder builder
    ) {
        configureTenantRoleManagementPolicy(builder);
        configureTenantPermissionManagementPolicy(builder);
        configureTenantRolePermissionPolicy(builder);
    }

    /**
     * Configures tenant role creation, modification and deletion.
     */
    private void configureTenantRoleManagementPolicy(
            GrpcMethodPolicy.Builder builder
    ) {
        requireAuthority(
                builder,
                IdentityAdministrationServiceGrpc
                        .getCreateTenantRoleMethod(),
                IdentityGrpcAuthorities.ROLE_MANAGE
        );

        requireAuthority(
                builder,
                IdentityAdministrationServiceGrpc
                        .getUpdateTenantRoleMethod(),
                IdentityGrpcAuthorities.ROLE_MANAGE
        );

        requireAuthority(
                builder,
                IdentityAdministrationServiceGrpc
                        .getDeleteTenantRoleMethod(),
                IdentityGrpcAuthorities.ROLE_MANAGE
        );
    }

    /**
     * Configures tenant permission creation, modification and deletion.
     */
    private void configureTenantPermissionManagementPolicy(
            GrpcMethodPolicy.Builder builder
    ) {
        requireAuthority(
                builder,
                IdentityAdministrationServiceGrpc
                        .getCreatePermissionMethod(),
                IdentityGrpcAuthorities.PERMISSION_MANAGE
        );

        requireAuthority(
                builder,
                IdentityAdministrationServiceGrpc
                        .getUpdatePermissionMethod(),
                IdentityGrpcAuthorities.PERMISSION_MANAGE
        );

        requireAuthority(
                builder,
                IdentityAdministrationServiceGrpc
                        .getDeletePermissionMethod(),
                IdentityGrpcAuthorities.PERMISSION_MANAGE
        );
    }

    /**
     * Configures granting and revoking permissions on tenant roles.
     */
    private void configureTenantRolePermissionPolicy(
            GrpcMethodPolicy.Builder builder
    ) {
        requireAuthority(
                builder,
                IdentityAdministrationServiceGrpc
                        .getGrantPermissionToRoleMethod(),
                IdentityGrpcAuthorities.PERMISSION_MANAGE
        );

        requireAuthority(
                builder,
                IdentityAdministrationServiceGrpc
                        .getRevokePermissionFromRoleMethod(),
                IdentityGrpcAuthorities.PERMISSION_MANAGE
        );
    }

    /**
     * Configures profile, account and role assignment administration.
     */
    private void configureUserAdministrationPolicy(
            GrpcMethodPolicy.Builder builder
    ) {
        requireAuthority(
                builder,
                IdentityAdministrationServiceGrpc
                        .getUpdateUserProfileAsAdministratorMethod(),
                IdentityGrpcAuthorities.USER_PROFILE_MANAGE
        );

        configureUserAccountPolicy(builder);
        configureRoleAssignmentPolicy(builder);
    }

    /**
     * Configures user account enable, disable and unlock operations.
     */
    private void configureUserAccountPolicy(
            GrpcMethodPolicy.Builder builder
    ) {
        requireAuthority(
                builder,
                IdentityAdministrationServiceGrpc
                        .getDisableUserAccountMethod(),
                IdentityGrpcAuthorities.USER_ACCOUNT_MANAGE
        );

        requireAuthority(
                builder,
                IdentityAdministrationServiceGrpc
                        .getEnableUserAccountMethod(),
                IdentityGrpcAuthorities.USER_ACCOUNT_MANAGE
        );

        requireAuthority(
                builder,
                IdentityAdministrationServiceGrpc
                        .getUnlockUserAccountMethod(),
                IdentityGrpcAuthorities.USER_ACCOUNT_MANAGE
        );
    }

    /**
     * Configures role assignment and revocation on tenant users.
     */
    private void configureRoleAssignmentPolicy(
            GrpcMethodPolicy.Builder builder
    ) {
        requireAuthority(
                builder,
                IdentityAdministrationServiceGrpc.getAssignRoleMethod(),
                IdentityGrpcAuthorities.ROLE_ASSIGN
        );

        requireAuthority(
                builder,
                IdentityAdministrationServiceGrpc.getRevokeRoleMethod(),
                IdentityGrpcAuthorities.ROLE_ASSIGN
        );
    }

    /**
     * Configures tenant membership status changes.
     */
    private void configureMembershipPolicy(
            GrpcMethodPolicy.Builder builder
    ) {
        requireAuthority(
                builder,
                IdentityAdministrationServiceGrpc
                        .getDisableMembershipMethod(),
                IdentityGrpcAuthorities.USER_DISABLE
        );

        requireAuthority(
                builder,
                IdentityAdministrationServiceGrpc
                        .getEnableMembershipMethod(),
                IdentityGrpcAuthorities.USER_DISABLE
        );

        requireAuthority(
                builder,
                IdentityAdministrationServiceGrpc
                        .getSuspendMembershipMethod(),
                IdentityGrpcAuthorities.USER_DISABLE
        );
    }

    /**
     * Configures internal token issuance and session administration.
     *
     * Token issuance currently authenticates the configured client secret in
     * the application service. Restrict the gRPC listener to a private network
     * or mTLS before exposing these methods outside the trusted service plane.
     */
    private void configureTokenPolicy(
            GrpcMethodPolicy.Builder builder
    ) {
        builder.permitAll(
                IdentityTokenServiceGrpc
                        .getIssueServiceTokenMethod()
                        .getFullMethodName()
        );
        builder.permitAll(
                IdentityTokenServiceGrpc
                        .getExchangeDelegatedTokenMethod()
                        .getFullMethodName()
        );
        requireAuthority(
                builder,
                IdentityTokenServiceGrpc.getListSessionsMethod(),
                IdentityGrpcAuthorities.SESSION_READ
        );
        requireAuthority(
                builder,
                IdentityTokenServiceGrpc.getRevokeSessionMethod(),
                IdentityGrpcAuthorities.SESSION_REVOKE
        );
        requireAuthority(
                builder,
                IdentityTokenServiceGrpc.getRevokeAllSessionsMethod(),
                IdentityGrpcAuthorities.SESSION_REVOKE
        );
    }

    /**
     * Configures platform-level role and permission administration.
     */
    private void configurePlatformPolicy(
            GrpcMethodPolicy.Builder builder
    ) {
        requireAuthority(
                builder,
                PlatformAuthorizationAdministrationServiceGrpc
                        .getListPlatformRolesMethod(),
                IdentityGrpcAuthorities.PLATFORM_ROLE_MANAGE
        );

        requireAuthority(
                builder,
                PlatformAuthorizationAdministrationServiceGrpc
                        .getCreatePlatformRoleMethod(),
                IdentityGrpcAuthorities.PLATFORM_ROLE_MANAGE
        );

        requireAuthority(
                builder,
                PlatformAuthorizationAdministrationServiceGrpc
                        .getGrantPlatformPermissionMethod(),
                IdentityGrpcAuthorities.PLATFORM_PERMISSION_MANAGE
        );

        requireAuthority(
                builder,
                PlatformAuthorizationAdministrationServiceGrpc
                        .getRevokePlatformPermissionMethod(),
                IdentityGrpcAuthorities.PLATFORM_PERMISSION_MANAGE
        );

        requireAuthority(
                builder,
                PlatformAuthorizationAdministrationServiceGrpc
                        .getGetGlobalPermissionMethod(),
                IdentityGrpcAuthorities.PLATFORM_PERMISSION_READ
        );
        requireAuthority(
                builder,
                PlatformAuthorizationAdministrationServiceGrpc
                        .getCreateGlobalPermissionMethod(),
                IdentityGrpcAuthorities.PLATFORM_PERMISSION_MANAGE
        );
        requireAuthority(
                builder,
                PlatformAuthorizationAdministrationServiceGrpc
                        .getUpdateGlobalPermissionMethod(),
                IdentityGrpcAuthorities.PLATFORM_PERMISSION_MANAGE
        );
        requireAuthority(
                builder,
                PlatformAuthorizationAdministrationServiceGrpc
                        .getDeleteGlobalPermissionMethod(),
                IdentityGrpcAuthorities.PLATFORM_PERMISSION_MANAGE
        );
    }

    /**
     * Adds an authority requirement for a gRPC method.
     */
    private void requireAuthority(
            GrpcMethodPolicy.Builder builder,
            MethodDescriptor<?, ?> method,
            String authority
    ) {
        String fullMethodName = method.getFullMethodName();

        log.debug(
                "Registering gRPC authority policy: "
                        + "method={}, authority={}",
                fullMethodName,
                authority
        );

        builder.requireAuthority(
                fullMethodName,
                authority
        );
    }

    /**
     * Adds an authentication requirement for a gRPC method.
     */
    private void requireAuthenticated(
            GrpcMethodPolicy.Builder builder,
            MethodDescriptor<?, ?> method
    ) {
        String fullMethodName = method.getFullMethodName();

        log.debug(
                "Registering authenticated gRPC method policy: method={}",
                fullMethodName
        );

        builder.requireAuthenticated(fullMethodName);
    }
}