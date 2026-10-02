package com.htv.smartfarm.gateway.grpc;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class GatewayGrpcExceptionMapper {
    public ResponseStatusException rest(String operation, StatusRuntimeException exception) {
        Status.Code code = exception.getStatus().getCode();
        HttpStatus status = switch (code) {
            case INVALID_ARGUMENT -> HttpStatus.BAD_REQUEST;
            case UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
            case PERMISSION_DENIED -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case ALREADY_EXISTS, FAILED_PRECONDITION, ABORTED -> HttpStatus.CONFLICT;
            case DEADLINE_EXCEEDED -> HttpStatus.GATEWAY_TIMEOUT;
            case UNAVAILABLE, UNIMPLEMENTED -> HttpStatus.BAD_GATEWAY;
            default -> HttpStatus.BAD_GATEWAY;
        };
        return new ResponseStatusException(status,
                operation + " failed: " + code + description(exception));
    }

    public GatewayGraphQlException graphQl(String operation, StatusRuntimeException exception) {
        Status.Code code = exception.getStatus().getCode();
        String classification = switch (code) {
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
        return new GatewayGraphQlException(operation + " failed" + description(exception), classification, exception);
    }

    private String description(StatusRuntimeException exception) {
        String value = exception.getStatus().getDescription();
        return value == null || value.isBlank() ? "" : ": " + value;
    }
}
