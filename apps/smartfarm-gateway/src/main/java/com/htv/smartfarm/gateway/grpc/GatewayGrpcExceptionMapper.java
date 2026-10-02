package com.htv.smartfarm.gateway.grpc;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class GatewayGrpcExceptionMapper {
    public GatewayRestException rest(String operation, StatusRuntimeException exception) {
        Status.Code grpc = exception.getStatus().getCode();
        HttpStatus http = switch (grpc) {
            case INVALID_ARGUMENT -> HttpStatus.BAD_REQUEST;
            case UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
            case PERMISSION_DENIED -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case ALREADY_EXISTS, FAILED_PRECONDITION, ABORTED -> HttpStatus.CONFLICT;
            case DEADLINE_EXCEEDED -> HttpStatus.GATEWAY_TIMEOUT;
            case UNAVAILABLE, UNIMPLEMENTED -> HttpStatus.BAD_GATEWAY;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return new GatewayRestException(http, code(grpc),
                operation + " failed" + description(exception), exception);
    }

    public GatewayGraphQlException graphQl(String operation, StatusRuntimeException exception) {
        return new GatewayGraphQlException(
                operation + " failed" + description(exception),
                code(exception.getStatus().getCode()), exception);
    }

    private String code(Status.Code value) {
        return switch (value) {
            case INVALID_ARGUMENT -> "BAD_REQUEST";
            case UNAUTHENTICATED -> "UNAUTHENTICATED";
            case PERMISSION_DENIED -> "FORBIDDEN";
            case NOT_FOUND -> "NOT_FOUND";
            case ALREADY_EXISTS -> "CONFLICT";
            case FAILED_PRECONDITION -> "FAILED_PRECONDITION";
            case ABORTED -> "VERSION_CONFLICT";
            case DEADLINE_EXCEEDED -> "TIMEOUT";
            case UNAVAILABLE, UNIMPLEMENTED -> "DOWNSTREAM_UNAVAILABLE";
            default -> "INTERNAL";
        };
    }

    private String description(StatusRuntimeException exception) {
        String value = exception.getStatus().getDescription();
        return value == null || value.isBlank() ? "" : ": " + value;
    }
}
