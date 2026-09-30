package com.htv.smartfarm.identity.grpc;

import java.util.concurrent.Callable;

import com.htv.smartfarm.common.exception.BusinessException;
import com.htv.smartfarm.common.exception.ConflictException;
import com.htv.smartfarm.common.exception.ForbiddenOperationException;
import com.htv.smartfarm.common.exception.NotFoundException;
import com.htv.smartfarm.common.exception.ValidationException;
import com.htv.smartfarm.identity.shared.exception.OptimisticConflictException;

import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

@Component
public final class GrpcExceptionMapper {

    private static final Logger log =
            LoggerFactory.getLogger(GrpcExceptionMapper.class);

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

        if (exception instanceof ValidationException
                || exception instanceof IllegalArgumentException) {
            return status(
                    Status.INVALID_ARGUMENT,
                    businessDescription(exception)
            );
        }

        if (exception instanceof NotFoundException) {
            return status(
                    Status.NOT_FOUND,
                    businessDescription(exception)
            );
        }

        if (exception instanceof ForbiddenOperationException) {
            return status(
                    Status.PERMISSION_DENIED,
                    businessDescription(exception)
            );
        }

        if (exception instanceof OptimisticConflictException
                || exception instanceof ObjectOptimisticLockingFailureException) {
            return Status.ABORTED
                    .withDescription(
                            "The resource was modified by another request"
                    )
                    .asRuntimeException();
        }

        if (exception instanceof ConflictException
                || exception instanceof DataIntegrityViolationException
                || exception instanceof IllegalStateException) {
            return status(
                    Status.FAILED_PRECONDITION,
                    businessDescription(exception)
            );
        }

        if (exception instanceof BusinessException) {
            return status(
                    Status.FAILED_PRECONDITION,
                    businessDescription(exception)
            );
        }

        log.error(
                "Unhandled exception while processing gRPC request",
                exception
        );

        return Status.INTERNAL
                .withDescription("Internal identity service error")
                .asRuntimeException();
    }

    private StatusRuntimeException status(
            Status status,
            String description
    ) {
        return status
                .withDescription(description)
                .asRuntimeException();
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
