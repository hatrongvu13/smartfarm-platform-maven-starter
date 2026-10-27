package com.htv.farmverse.gateway.authorization;

import com.htv.farmverse.proto.events.v1.DomainEvent;
import com.htv.farmverse.security.authorization.AuthorizationVersionWatermarkStore;
import org.springframework.stereotype.Component;

@Component
public class AuthorizationInvalidationConsumer {
    private final AuthorizationVersionWatermarkStore watermarks;
    public AuthorizationInvalidationConsumer(AuthorizationVersionWatermarkStore watermarks) { this.watermarks = watermarks; }
    public boolean accept(DomainEvent event) {
        if (event == null || !event.hasAuthorizationInvalidated()) return false;
        var value = event.getAuthorizationInvalidated();
        watermarks.observeTenant(value.getTenantId(), value.getTenantAuthorizationVersion());
        if (!value.getSubjectId().isBlank()) watermarks.observeSubject(
                value.getTenantId(), value.getSubjectId(), value.getSubjectAuthorizationVersion());
        return true;
    }
}
