package com.htv.smartfarm.security.grpc.dynamic;

public record GrpcAuthorizationDecision(
        Outcome outcome,
        String reasonCode
) {
    public enum Outcome { ALLOW, DENY, UNAVAILABLE }

    public GrpcAuthorizationDecision {
        outcome = outcome == null ? Outcome.UNAVAILABLE : outcome;
        reasonCode = reasonCode == null || reasonCode.isBlank()
                ? outcome.name()
                : reasonCode.trim();
    }

    public static GrpcAuthorizationDecision allow(String reason) {
        return new GrpcAuthorizationDecision(Outcome.ALLOW, reason);
    }

    public static GrpcAuthorizationDecision deny(String reason) {
        return new GrpcAuthorizationDecision(Outcome.DENY, reason);
    }

    public static GrpcAuthorizationDecision unavailable(String reason) {
        return new GrpcAuthorizationDecision(Outcome.UNAVAILABLE, reason);
    }

    public boolean allowed() { return outcome == Outcome.ALLOW; }
    public boolean denied() { return outcome == Outcome.DENY; }
    public boolean unavailable() { return outcome == Outcome.UNAVAILABLE; }
}
