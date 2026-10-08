package com.htv.smartfarm.security.grpc;

import static org.assertj.core.api.Assertions.assertThat;

import io.grpc.Status;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * Pins the single source-of-truth gRPC {@link Status.Code} -> HTTP / stable-code
 * mapping that the gateway REST + GraphQL facades all delegate to. Before this was
 * common-ised, each facade kept a drifted copy of this table.
 */
class GrpcStatusHttpMappingTest {

    @Test
    void mapsClientErrorsToTheirHttpStatus() {
        assertThat(GrpcStatusHttpMapping.httpStatus(Status.Code.INVALID_ARGUMENT))
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(GrpcStatusHttpMapping.httpStatus(Status.Code.OUT_OF_RANGE))
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(GrpcStatusHttpMapping.httpStatus(Status.Code.UNAUTHENTICATED))
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(GrpcStatusHttpMapping.httpStatus(Status.Code.PERMISSION_DENIED))
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(GrpcStatusHttpMapping.httpStatus(Status.Code.NOT_FOUND))
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void mapsTheThreeConflictCodesToConflict() {
        assertThat(GrpcStatusHttpMapping.httpStatus(Status.Code.ALREADY_EXISTS))
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(GrpcStatusHttpMapping.httpStatus(Status.Code.FAILED_PRECONDITION))
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(GrpcStatusHttpMapping.httpStatus(Status.Code.ABORTED))
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void mapsInfrastructureCodes() {
        assertThat(GrpcStatusHttpMapping.httpStatus(Status.Code.RESOURCE_EXHAUSTED))
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(GrpcStatusHttpMapping.httpStatus(Status.Code.DEADLINE_EXCEEDED))
                .isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(GrpcStatusHttpMapping.httpStatus(Status.Code.UNAVAILABLE))
                .isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(GrpcStatusHttpMapping.httpStatus(Status.Code.UNIMPLEMENTED))
                .isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    void mapsUnknownAndNullToInternalServerError() {
        assertThat(GrpcStatusHttpMapping.httpStatus(Status.Code.INTERNAL))
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(GrpcStatusHttpMapping.httpStatus(Status.Code.UNKNOWN))
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(GrpcStatusHttpMapping.httpStatus(Status.Code.DATA_LOSS))
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(GrpcStatusHttpMapping.httpStatus(null))
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void keepsConflictCodesDistinguishableInTheStableCode() {
        // Three gRPC codes share HTTP 409 but MUST stay distinct to a client.
        assertThat(GrpcStatusHttpMapping.stableCode(Status.Code.ALREADY_EXISTS)).isEqualTo("CONFLICT");
        assertThat(GrpcStatusHttpMapping.stableCode(Status.Code.FAILED_PRECONDITION)).isEqualTo("FAILED_PRECONDITION");
        assertThat(GrpcStatusHttpMapping.stableCode(Status.Code.ABORTED)).isEqualTo("VERSION_CONFLICT");
    }

    @Test
    void stableCodeCoversTransportAndFallback() {
        assertThat(GrpcStatusHttpMapping.stableCode(Status.Code.UNAUTHENTICATED)).isEqualTo("UNAUTHENTICATED");
        assertThat(GrpcStatusHttpMapping.stableCode(Status.Code.PERMISSION_DENIED)).isEqualTo("FORBIDDEN");
        assertThat(GrpcStatusHttpMapping.stableCode(Status.Code.DEADLINE_EXCEEDED)).isEqualTo("TIMEOUT");
        assertThat(GrpcStatusHttpMapping.stableCode(Status.Code.UNAVAILABLE)).isEqualTo("DOWNSTREAM_UNAVAILABLE");
        assertThat(GrpcStatusHttpMapping.stableCode(Status.Code.INTERNAL)).isEqualTo("INTERNAL");
        assertThat(GrpcStatusHttpMapping.stableCode(null)).isEqualTo("INTERNAL");
    }
}
