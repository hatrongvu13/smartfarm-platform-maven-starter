package com.htv.farmverse.identity.authorization.application;

public interface FarmDirectoryPort {

    boolean existsInTenant(
            String tenantId,
            String farmId
    );
}