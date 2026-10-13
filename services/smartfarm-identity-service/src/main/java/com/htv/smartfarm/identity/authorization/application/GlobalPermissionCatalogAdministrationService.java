package com.htv.smartfarm.identity.authorization.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.htv.smartfarm.common.exception.ConflictException;
import com.htv.smartfarm.identity.authorization.domain.PermissionCode;
import com.htv.smartfarm.identity.authorization.domain.PermissionEntity;
import com.htv.smartfarm.identity.authorization.repository.PermissionRepository;
import com.htv.smartfarm.identity.authorization.repository.RolePermissionRepository;
import com.htv.smartfarm.identity.authorization.version.AuthorizationMutationCoordinator;
import com.htv.smartfarm.identity.messaging.event.IdentityIntegrationEventPublisher;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Platform-owned transactional boundary for the global permission catalog. */
@Service
@RequiredArgsConstructor
public class GlobalPermissionCatalogAdministrationService {
    private static final String PLATFORM_TENANT = "_platform";
    private static final String AGGREGATE_TYPE = "permission";

    private final PermissionRepository permissions;
    private final RolePermissionRepository grants;
    private final PermissionCatalogMutationService mutations;
    private final AuthorizationMutationCoordinator authorizationMutations;
    private final IdentityIntegrationEventPublisher events;

    @Transactional(readOnly = true)
    public PermissionEntity get(String code) {
        String normalized = PermissionCode.normalize(code);
        return permissions.findById(normalized)
                .orElseThrow(() -> com.htv.smartfarm.common.exception.NotFoundException
                        .entity("Permission", normalized));
    }

    @Transactional
    public PermissionEntity create(
            String code,
            String resourceType,
            String action,
            String description,
            String actorId,
            String correlationId
    ) {
        PermissionEntity value = mutations.createGlobal(
                code, resourceType, action, description
        );
        publish("identity.permission.created", value, actorId, correlationId);
        return value;
    }

    @Transactional
    public PermissionEntity update(
            String code,
            String resourceType,
            String action,
            String description,
            Long expectedVersion,
            String actorId,
            String correlationId
    ) {
        PermissionEntity current = get(code);
        requireVersion(current, expectedVersion);
        List<String> affectedTenants = grants
                .findDistinctTenantIdsByPermissionCode(current.getCode());
        PermissionEntity value = mutations.updateGlobal(
                code, resourceType, action, description
        );
        invalidate(affectedTenants, actorId, correlationId,
                "GLOBAL_PERMISSION_UPDATED");
        publish("identity.permission.updated", value, actorId, correlationId);
        return value;
    }

    @Transactional
    public boolean delete(
            String code,
            Long expectedVersion,
            String actorId,
            String correlationId
    ) {
        PermissionEntity current = get(code);
        requireVersion(current, expectedVersion);
        List<String> affectedTenants = grants
                .findDistinctTenantIdsByPermissionCode(current.getCode());
        boolean deleted = mutations.deleteGlobal(code);
        if (deleted) {
            invalidate(affectedTenants, actorId, correlationId,
                    "GLOBAL_PERMISSION_DELETED");
            publishDeleted(current, affectedTenants, actorId, correlationId);
        }
        return deleted;
    }

    private void requireVersion(PermissionEntity value, Long expectedVersion) {
        if (expectedVersion != null && value.getVersion() != expectedVersion) {
            throw new ConflictException(
                    "PERMISSION_VERSION_CONFLICT",
                    "Permission was updated by another request"
            );
        }
    }

    private void invalidate(
            List<String> tenantIds,
            String actorId,
            String correlationId,
            String reason
    ) {
        tenantIds.forEach(tenantId -> authorizationMutations.tenantChanged(
                tenantId, actorId, correlationId, reason
        ));
    }

    private void publish(
            String eventType,
            PermissionEntity value,
            String actorId,
            String correlationId
    ) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("permissionCode", value.getCode());
        data.put("resourceType", value.getResourceType());
        data.put("action", value.getAction());
        events.publish(
                PLATFORM_TENANT, actorId, correlationId, null,
                eventType, AGGREGATE_TYPE, value.getCode(), value.getVersion(), data
        );
    }

    private void publishDeleted(
            PermissionEntity value,
            List<String> affectedTenants,
            String actorId,
            String correlationId
    ) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("permissionCode", value.getCode());
        data.put("resourceType", value.getResourceType());
        data.put("action", value.getAction());
        data.put("affectedTenantIds", String.join(",", affectedTenants));
        events.publish(
                PLATFORM_TENANT, actorId, correlationId, null,
                "identity.permission.deleted", AGGREGATE_TYPE,
                value.getCode(), value.getVersion(), data
        );
    }
}
