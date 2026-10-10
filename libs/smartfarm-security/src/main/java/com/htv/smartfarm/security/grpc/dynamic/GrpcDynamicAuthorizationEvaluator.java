package com.htv.smartfarm.security.grpc.dynamic;

import com.htv.smartfarm.security.core.SecurityIdentity;

/** Live authorization evaluator used beside static JWT authority checks. */
@FunctionalInterface
public interface GrpcDynamicAuthorizationEvaluator {
    GrpcAuthorizationDecision evaluate(
            String fullMethodName,
            String requiredAuthority,
            SecurityIdentity identity
    );

    static GrpcDynamicAuthorizationEvaluator unavailable() {
        return (method, authority, identity) ->
                GrpcAuthorizationDecision.unavailable("DYNAMIC_EVALUATOR_NOT_CONFIGURED");
    }
}
