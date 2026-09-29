package com.htv.smartfarm.identity.shared.exception;

public class EntityNotFoundException extends RuntimeException {

    public EntityNotFoundException(
            String entityName,
            String identifier
    ) {
        super(entityName + " not found: " + identifier);
    }
}