package com.htv.smartfarm.security.authorization;

public interface AuthorizationVersionWatermarkStore {
    long tenantVersion(String tenantId);
    long subjectVersion(String tenantId, String subjectId);
    long observeTenant(String tenantId, long version);
    long observeSubject(String tenantId, String subjectId, long version);
    default boolean isStale(String tenantId, String subjectId,
            long tokenTenantVersion, long tokenSubjectVersion) {
        return tokenTenantVersion < tenantVersion(tenantId)
                || tokenSubjectVersion < subjectVersion(tenantId, subjectId);
    }
}
