package com.htv.smartfarm.identity.authorization.application;

import java.util.Objects;
import java.util.function.Supplier;

import com.htv.smartfarm.identity.authorization.version.AuthorizationMutationCoordinator;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transaction boundary for runtime permission-catalog administration.
 * <p>
 * The catalog remains globally stored, but every runtime administration command must identify
 * the tenant whose effective policy is being changed. Startup/bootstrap synchronization must not
 * use this service because it has no authenticated tenant or actor context.
 */
@Service
@RequiredArgsConstructor
public class TenantPermissionCatalogAdministrationService {

    private final AuthorizationMutationCoordinator authorizationMutations;

    @Transactional
    public <T> T apply(
            String tenantId,
            String actorId,
            String correlationId,
            String reasonCode,
            Supplier<T> mutation
    ) {
        requireText(tenantId, "tenantId");
        requireText(actorId, "actorId");
        requireText(reasonCode, "reasonCode");
        T result = Objects.requireNonNull(mutation, "mutation").get();
        authorizationMutations.tenantChanged(
                tenantId, actorId, correlationId, reasonCode
        );
        return result;
    }

    @Transactional
    public void apply(
            String tenantId,
            String actorId,
            String correlationId,
            String reasonCode,
            Runnable mutation
    ) {
        apply(tenantId, actorId, correlationId, reasonCode, () -> {
            Objects.requireNonNull(mutation, "mutation").run();
            return Boolean.TRUE;
        });
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
