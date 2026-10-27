package com.htv.farmverse.security.authorization;

import com.htv.farmverse.security.core.DelegatedActor;
import com.htv.farmverse.security.core.SecurityIdentity;

/** Rejects user/delegated identities older than locally observed watermarks. */
public final class AuthorizationFreshnessGuard {
    private final AuthorizationVersionWatermarkStore store;
    public AuthorizationFreshnessGuard(AuthorizationVersionWatermarkStore store) {
        this.store = java.util.Objects.requireNonNull(store, "store");
    }
    public Decision evaluate(SecurityIdentity identity) {
        if (identity.isUser()) return evaluate(identity.tenantId(), identity.subject(),
                identity.tenantAuthorizationVersion(), identity.authorizationVersion());
        DelegatedActor actor = identity.delegatedActor();
        if (identity.isService() && actor != null) return evaluate(identity.tenantId(), actor.subject(),
                actor.tenantAuthorizationVersion(), actor.authorizationVersion());
        return new Decision(true, "MACHINE_IDENTITY");
    }
    private Decision evaluate(String tenant, String subject, Long tenantVersion, Long subjectVersion) {
        long requiredTenant=store.tenantVersion(tenant), requiredSubject=store.subjectVersion(tenant,subject);
        if ((requiredTenant>0 && tenantVersion==null)||(requiredSubject>0 && subjectVersion==null))
            return new Decision(false,"AUTHORIZATION_VERSION_MISSING");
        if ((tenantVersion==null?0:tenantVersion)<requiredTenant)
            return new Decision(false,"TENANT_AUTHORIZATION_STALE");
        if ((subjectVersion==null?0:subjectVersion)<requiredSubject)
            return new Decision(false,"SUBJECT_AUTHORIZATION_STALE");
        return new Decision(true,"FRESH");
    }
    public record Decision(boolean allowed,String reasonCode) { }
}
