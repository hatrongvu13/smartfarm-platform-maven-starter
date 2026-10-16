package com.htv.smartfarm.identity.grpc;

import java.util.concurrent.Callable;

import com.htv.smartfarm.common.exception.BusinessException;
import com.htv.smartfarm.common.exception.ConflictException;
import com.htv.smartfarm.common.exception.ForbiddenOperationException;
import com.htv.smartfarm.common.exception.NotFoundException;
import com.htv.smartfarm.common.exception.ValidationException;
import com.htv.smartfarm.identity.shared.exception.OptimisticConflictException;

import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public final class GrpcExceptionMapper {

    private static final Metadata.Key<String> ERROR_CODE_TRAILER =
            Metadata.Key.of(
                    "identity-error-code",
                    Metadata.ASCII_STRING_MARSHALLER
            );

    public <T> void executeUnary(
            StreamObserver<T> responseObserver,
            Callable<T> action
    ) {
        try {
            T response = action.call();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Throwable exception) {
            responseObserver.onError(toStatusException(exception));
        }
    }

    public StatusRuntimeException toStatusException(Throwable exception) {
        if (exception instanceof StatusRuntimeException statusRuntimeException) {
            return statusRuntimeException;
        }

        if (exception instanceof StatusException statusException) {
            return statusException.getStatus()
                    .asRuntimeException(statusException.getTrailers());
        }

        if (exception instanceof SecurityException) {
            return status(
                    Status.UNAUTHENTICATED,
                    "Authentication failed",
                    "UNAUTHENTICATED"
            );
        }

        if (exception instanceof ValidationException
                || exception instanceof IllegalArgumentException) {
            return status(
                    Status.INVALID_ARGUMENT,
                    businessDescription(exception),
                    errorCode(exception, "INVALID_ARGUMENT")
            );
        }

        if (exception instanceof NotFoundException) {
            return status(
                    Status.NOT_FOUND,
                    businessDescription(exception),
                    errorCode(exception, "NOT_FOUND")
            );
        }

        if (exception instanceof ForbiddenOperationException) {
            return status(
                    Status.PERMISSION_DENIED,
                    businessDescription(exception),
                    errorCode(exception, "PERMISSION_DENIED")
            );
        }

        if (exception instanceof OptimisticConflictException
                || exception instanceof ObjectOptimisticLockingFailureException) {
            return status(
                    Status.ABORTED,
                    "The resource was modified by another request",
                    "RESOURCE_VERSION_CONFLICT"
            );
        }

        if (exception instanceof ConflictException
                || exception instanceof DataIntegrityViolationException
                || exception instanceof IllegalStateException) {
            return status(
                    Status.FAILED_PRECONDITION,
                    businessDescription(exception),
                    errorCode(exception, "FAILED_PRECONDITION")
            );
        }

        if (exception instanceof BusinessException) {
            return status(
                    Status.FAILED_PRECONDITION,
                    businessDescription(exception),
                    errorCode(exception, "FAILED_PRECONDITION")
            );
        }

        log.error(
                "Unhandled exception while processing gRPC request",
                exception
        );

        return status(
                Status.INTERNAL,
                "Internal identity service error",
                "INTERNAL"
        );
    }

    private StatusRuntimeException status(
            Status status,
            String description,
            String errorCode
    ) {
        Metadata trailers = new Metadata();
        trailers.put(ERROR_CODE_TRAILER, errorCode);
        return status
                .withDescription(description)
                .asRuntimeException(trailers);
    }

    private String errorCode(Throwable exception, String fallback) {
        return exception instanceof BusinessException businessException
                ? businessException.code()
                : fallback;
    }

    private String businessDescription(Throwable exception) {
        if (exception instanceof BusinessException businessException) {
            return businessException.code()
                    + ": "
                    + businessException.getMessage();
        }

        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? "Request could not be processed"
                : message;
    }
}
