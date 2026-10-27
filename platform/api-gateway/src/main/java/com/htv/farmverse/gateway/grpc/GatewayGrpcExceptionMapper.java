package com.htv.farmverse.gateway.grpc;

import com.htv.farmverse.security.grpc.GrpcStatusHttpMapping;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Translates a downstream gRPC {@link StatusRuntimeException} into the gateway's
 * REST / GraphQL error types. The gRPC-code → HTTP-status and gRPC-code →
 * stable-code tables are NOT kept here: they live in the shared
 * {@link GrpcStatusHttpMapping} so the gateway and every REST facade map a given
 * downstream failure to exactly the same client-facing contract.
 */
@Component
public class GatewayGrpcExceptionMapper {
    private static final Metadata.Key<String> IDENTITY_ERROR_CODE =
            Metadata.Key.of("identity-error-code", Metadata.ASCII_STRING_MARSHALLER);

    public GatewayRestException rest(String operation, StatusRuntimeException exception) {
        Status.Code grpc = exception.getStatus().getCode();
        HttpStatus http = GrpcStatusHttpMapping.httpStatus(grpc);
        return new GatewayRestException(http, stableCode(exception),
                operation + " failed" + description(exception), exception);
    }

    public GatewayGraphQlException graphQl(String operation, StatusRuntimeException exception) {
        return new GatewayGraphQlException(
                operation + " failed" + description(exception),
                stableCode(exception), exception);
    }

    private String stableCode(StatusRuntimeException exception) {
        Metadata trailers = exception.getTrailers();
        String identityCode = trailers == null ? null : trailers.get(IDENTITY_ERROR_CODE);
        return identityCode == null || identityCode.isBlank()
                ? GrpcStatusHttpMapping.stableCode(exception.getStatus().getCode())
                : identityCode;
    }

    private String description(StatusRuntimeException exception) {
        String value = exception.getStatus().getDescription();
        return value == null || value.isBlank() ? "" : ": " + value;
    }
}
