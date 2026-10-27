package com.htv.farmverse.security.authorization;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class InMemoryAuthorizationVersionWatermarkStore
        implements AuthorizationVersionWatermarkStore {
    private record SubjectKey(String tenantId, String subjectId) {
        SubjectKey {
            tenantId = required(tenantId, "tenantId");
            subjectId = required(subjectId, "subjectId");
        }
    }
    private final ConcurrentMap<String, Long> tenants = new ConcurrentHashMap<>();
    private final ConcurrentMap<SubjectKey, Long> subjects = new ConcurrentHashMap<>();
    public long tenantVersion(String tenantId) { return tenants.getOrDefault(required(tenantId,"tenantId"), 0L); }
    public long subjectVersion(String tenantId,String subjectId) { return subjects.getOrDefault(new SubjectKey(tenantId,subjectId), 0L); }
    public long observeTenant(String tenantId,long version) { valid(version); return tenants.merge(required(tenantId,"tenantId"),version,Math::max); }
    public long observeSubject(String tenantId,String subjectId,long version) { valid(version); return subjects.merge(new SubjectKey(tenantId,subjectId),version,Math::max); }
    private static String required(String v,String f) { if(v==null||v.isBlank()) throw new IllegalArgumentException(f+" must not be blank"); return v.trim(); }
    private static void valid(long v) { if(v<0) throw new IllegalArgumentException("authorization version must not be negative"); }
}
