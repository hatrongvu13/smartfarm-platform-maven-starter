package com.htv.farmverse.identity.administration.application;

import com.htv.farmverse.identity.administration.application.command.AssignRoleCommand;
import com.htv.farmverse.identity.administration.application.command.CreateTenantUserCommand;
import com.htv.farmverse.identity.administration.application.command.RevokeRoleCommand;
import com.htv.farmverse.identity.administration.application.model.CreatedTenantUser;
import com.htv.farmverse.identity.administration.application.model.RoleData;
import com.htv.farmverse.identity.administration.application.model.UserAuthorizationData;
import com.htv.farmverse.identity.administration.application.model.UserSummaryData;
import com.htv.farmverse.common.paging.PageResult;
import com.htv.farmverse.identity.tenant.domain.MembershipStatus;

public interface TenantIdentityAdministrationService {

    PageResult<RoleData> listRoles(
            String tenantId,
            int pageSize,
            String pageToken,
            boolean includePermissions
    );

    RoleData getRole(
            String tenantId,
            String roleId,
            String roleCode
    );

    PageResult<UserSummaryData> listUsers(
            String tenantId,
            int pageSize,
            String pageToken,
            String searchText,
            MembershipStatus membershipStatus,
            String roleCode
    );

    UserAuthorizationData getUserAuthorization(
            String tenantId,
            String subjectId
    );

    CreatedTenantUser createUser(
            CreateTenantUserCommand command
    );

    UserAuthorizationData assignRole(
            AssignRoleCommand command
    );

    UserAuthorizationData revokeRole(
            RevokeRoleCommand command
    );

    UserAuthorizationData disableMembership(
            String tenantId,
            String subjectId,
            String actorId,
            String reason
    );

    UserAuthorizationData enableMembership(
            String tenantId,
            String subjectId,
            String actorId
    );

    UserAuthorizationData suspendMembership(
            String tenantId,
            String subjectId,
            String actorId,
            String reason
    );

    UserAuthorizationData disableAccount(String tenantId, String subjectId, String actorId);

    UserAuthorizationData enableAccount(String tenantId, String subjectId, String actorId);

    UserAuthorizationData unlockAccount(String tenantId, String subjectId, String actorId);

}