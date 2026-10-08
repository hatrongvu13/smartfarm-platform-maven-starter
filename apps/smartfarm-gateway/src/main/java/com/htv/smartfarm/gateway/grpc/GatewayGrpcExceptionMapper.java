package com.htv.smartfarm.gateway.grpc;

import com.htv.smartfarm.security.grpc.GrpcStatusHttpMapping;
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
    public GatewayRestException rest(String operation, StatusRuntimeException exception) {
        Status.Code grpc = exception.getStatus().getCode();
        HttpStatus http = GrpcStatusHttpMapping.httpStatus(grpc);
        return new GatewayRestException(http, GrpcStatusHttpMapping.stableCode(grpc),
                operation + " failed" + description(exception), exception);
    }

    public GatewayGraphQlException graphQl(String operation, StatusRuntimeException exception) {
        return new GatewayGraphQlException(
                operation + " failed" + description(exception),
                GrpcStatusHttpMapping.stableCode(exception.getStatus().getCode()), exception);
    }

    private String description(StatusRuntimeException exception) {
        String value = exception.getStatus().getDescription();
        return value == null || value.isBlank() ? "" : ": " + value;
    }
}
