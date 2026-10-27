package com.htv.farmverse.identity.authorization.application;

public interface AuthorizationMutationPort {
    void subjectChanged(String tenantId, String subjectId, String actorId,
                        String correlationId, String reasonCode);
    void tenantChanged(String tenantId, String actorId,
                       String correlationId, String reasonCode);
}
