package com.htv.farmverse.security.grpc.dynamic;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

import com.htv.farmverse.security.core.SecurityIdentity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Bounded-cardinality counters and structured divergence logs for Phase 3C. */
public final class GrpcAuthorizationShadowObserver {
    private static final Logger log = LoggerFactory.getLogger(
            "farmverse.audit.dynamic-authorization"
    );

    private final ConcurrentHashMap<String, LongAdder> counters =
            new ConcurrentHashMap<>();

    public void observe(
            String method,
            SecurityIdentity identity,
            boolean staticAllowed,
            GrpcAuthorizationDecision dynamic
    ) {
        String dynamicValue = dynamic.outcome().name().toLowerCase(java.util.Locale.ROOT);
        String key = "static_" + (staticAllowed ? "allow" : "deny")
                + "_dynamic_" + dynamicValue;
        counters.computeIfAbsent(key, ignored -> new LongAdder()).increment();

        boolean divergent = dynamic.unavailable()
                || staticAllowed != dynamic.allowed();
        if (divergent) {
            log.warn(
                    "authorization_shadow method={} tenant={} machine_subject={} "
                            + "effective_actor={} static_allowed={} dynamic_outcome={} reason={}",
                    method,
                    identity.tenantId(),
                    identity.machineSubject(),
                    identity.effectiveActorSubject(),
                    staticAllowed,
                    dynamic.outcome(),
                    dynamic.reasonCode()
            );
        } else {
            log.debug(
                    "authorization_shadow method={} static_allowed={} dynamic_outcome={} reason={}",
                    method, staticAllowed, dynamic.outcome(), dynamic.reasonCode()
            );
        }
    }

    public long count(String key) {
        LongAdder counter = counters.get(key);
        return counter == null ? 0L : counter.sum();
    }
}
