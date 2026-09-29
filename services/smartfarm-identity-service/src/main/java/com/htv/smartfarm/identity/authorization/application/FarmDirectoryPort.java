package com.htv.smartfarm.identity.authorization.application;

public interface FarmDirectoryPort {

    boolean existsInTenant(
            String tenantId,
            String farmId
    );
}