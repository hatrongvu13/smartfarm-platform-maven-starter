package com.htv.smartfarm.security.grpc;

import io.grpc.Status;
import org.springframework.http.HttpStatus;

/**
 * Single source of truth for translating a downstream gRPC {@link Status.Code}
 * into the edge HTTP status and the stable, client-facing error code string.
 *
 * <p>Before this class every REST/GraphQL facade in the gateway kept its own
 * copy of the {@code switch (code)} mapping. The copies had drifted — some
 * returned {@code 502 BAD_GATEWAY} for an unknown code, some {@code 500}; some
 * handled {@code ALREADY_EXISTS}/{@code ABORTED}, some did not — so the same
 * downstream failure surfaced as different HTTP codes depending on which
 * facade a client hit. Routing every mapping through this one table keeps the
 * platform's inter-service error contract consistent and un-driftable.
 *
 * <p>Pure and stateless: no Spring beans, no I/O. Lives in the shared security
 * lib because both the gateway and any future service-side REST facade need it.
 */
public final class GrpcStatusHttpMapping {

    private GrpcStatusHttpMapping() {
    }

    /**
     * The edge HTTP status for a downstream gRPC status code.
     *
     * <p>An unknown/unexpected code maps to {@code 500 INTERNAL_SERVER_ERROR}:
     * it is a genuine server-side fault, not a bad gateway hop.
     */
    public static HttpStatus httpStatus(Status.Code code) {
        if (code == null) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return switch (code) {
            case INVALID_ARGUMENT, OUT_OF_RANGE -> HttpStatus.BAD_REQUEST;
            case UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
            case PERMISSION_DENIED -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case ALREADY_EXISTS, FAILED_PRECONDITION, ABORTED -> HttpStatus.CONFLICT;
            case RESOURCE_EXHAUSTED -> HttpStatus.TOO_MANY_REQUESTS;
            case DEADLINE_EXCEEDED -> HttpStatus.GATEWAY_TIMEOUT;
            case UNAVAILABLE, UNIMPLEMENTED -> HttpStatus.BAD_GATEWAY;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    /**
     * The stable, machine-readable error code string returned to clients.
     *
     * <p>Decoupled from {@link #httpStatus} on purpose: several gRPC codes
     * share one HTTP status (e.g. three map to {@code 409}) but must stay
     * distinguishable to a client (CONFLICT vs FAILED_PRECONDITION vs
     * VERSION_CONFLICT). This preserves the gateway mapper's original code
     * vocabulary so the public contract does not change.
     */
    public static String stableCode(Status.Code code) {
        if (code == null) {
            return "INTERNAL";
        }
        return switch (code) {
            case INVALID_ARGUMENT, OUT_OF_RANGE -> "BAD_REQUEST";
            case UNAUTHENTICATED -> "UNAUTHENTICATED";
            case PERMISSION_DENIED -> "FORBIDDEN";
            case NOT_FOUND -> "NOT_FOUND";
            case ALREADY_EXISTS -> "CONFLICT";
            case FAILED_PRECONDITION -> "FAILED_PRECONDITION";
            case ABORTED -> "VERSION_CONFLICT";
            case RESOURCE_EXHAUSTED -> "RATE_LIMITED";
            case DEADLINE_EXCEEDED -> "TIMEOUT";
            case UNAVAILABLE, UNIMPLEMENTED -> "DOWNSTREAM_UNAVAILABLE";
            default -> "INTERNAL";
        };
    }
}
