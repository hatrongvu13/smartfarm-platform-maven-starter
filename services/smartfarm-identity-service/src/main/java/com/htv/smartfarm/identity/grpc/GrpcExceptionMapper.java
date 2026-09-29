package com.htv.smartfarm.identity.grpc;

import com.htv.smartfarm.identity.shared.exception
        .AccessDeniedException;
import com.htv.smartfarm.identity.shared.exception
        .ConflictException;
import com.htv.smartfarm.identity.shared.exception
        .EntityNotFoundException;
import com.htv.smartfarm.identity.shared.exception
        .OptimisticConflictException;

import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm
        .ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

@Component
public class GrpcExceptionMapper {

    private static final Logger log =
            LoggerFactory.getLogger(
                    GrpcExceptionMapper.class
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
            responseObserver.onError(
                    toStatusException(exception)
            );
        }
    }

    public StatusRuntimeException toStatusException(
            Throwable exception
    ) {
        if (exception instanceof StatusRuntimeException status) {
            return status;
        }

        if (exception instanceof StatusException status) {
            return status.getStatus()
                    .asRuntimeException(
                            status.getTrailers()
                    );
        }

        if (exception instanceof EntityNotFoundException) {
            return Status.NOT_FOUND
                    .withDescription(exception.getMessage())
                    .asRuntimeException();
        }

        if (exception instanceof IllegalArgumentException) {
            return Status.INVALID_ARGUMENT
                    .withDescription(exception.getMessage())
                    .asRuntimeException();
        }

        if (exception instanceof AccessDeniedException denied) {
            return Status.PERMISSION_DENIED
                    .withDescription(
                            denied.getReasonCode()
                                    + ": "
                                    + denied.getMessage()
                    )
                    .asRuntimeException();
        }

        if (exception
                instanceof OptimisticConflictException
                || exception
                instanceof ObjectOptimisticLockingFailureException) {
            return Status.ABORTED
                    .withDescription(
                            "Resource was modified by another request"
                    )
                    .asRuntimeException();
        }

        if (exception instanceof ConflictException
                || exception
                instanceof DataIntegrityViolationException
                || exception instanceof IllegalStateException) {
            return Status.FAILED_PRECONDITION
                    .withDescription(exception.getMessage())
                    .asRuntimeException();
        }

        log.error(
                "Unhandled gRPC service exception",
                exception
        );

        return Status.INTERNAL
                .withDescription(
                        "Internal identity service error"
                )
                .asRuntimeException();
    }
}