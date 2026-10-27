package com.htv.farmverse.security.authorization;

/** Transport-neutral sink for an AuthorizationInvalidated event consumer. */
public final class AuthorizationInvalidationObserver {
    private final AuthorizationVersionWatermarkStore store;
    public AuthorizationInvalidationObserver(AuthorizationVersionWatermarkStore store) {
        this.store=java.util.Objects.requireNonNull(store,"store");
    }
    public void observe(String tenant,String subject,long tenantVersion,long subjectVersion) {
        store.observeTenant(tenant,tenantVersion);
        if(subject!=null&&!subject.isBlank()) store.observeSubject(tenant,subject,subjectVersion);
    }
}
