package com.htv.smartfarm.identity.grpc;

import static org.assertj.core.api.Assertions.assertThat;

import com.htv.smartfarm.common.exception.ConflictException;
import com.htv.smartfarm.common.exception.ForbiddenOperationException;
import com.htv.smartfarm.common.exception.NotFoundException;
import com.htv.smartfarm.common.exception.ValidationException;

import io.grpc.Status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GrpcExceptionMapperTest {

    private GrpcExceptionMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new GrpcExceptionMapper();
    }

    @Test
    void shouldMapValidationException() {
        var result = mapper.toStatusException(
                new ValidationException(
                        "INVALID_EMAIL",
                        "Email is invalid"
                )
        );

        assertThat(result.getStatus().getCode())
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertThat(result.getStatus().getDescription())
                .isEqualTo("INVALID_EMAIL: Email is invalid");
    }

    @Test
    void shouldMapNotFoundException() {
        var result = mapper.toStatusException(
                NotFoundException.entity("User", "user-001")
        );

        assertThat(result.getStatus().getCode())
                .isEqualTo(Status.Code.NOT_FOUND);
        assertThat(result.getStatus().getDescription())
                .isEqualTo("USER_NOT_FOUND: User not found: user-001");
    }

    @Test
    void shouldMapForbiddenOperationException() {
        var result = mapper.toStatusException(
                new ForbiddenOperationException(
                        "ROLE_ASSIGNMENT_DENIED",
                        "Role cannot be assigned"
                )
        );

        assertThat(result.getStatus().getCode())
                .isEqualTo(Status.Code.PERMISSION_DENIED);
    }

    @Test
    void shouldMapConflictException() {
        var result = mapper.toStatusException(
                new ConflictException(
                        "EMAIL_ALREADY_EXISTS",
                        "Email already exists"
                )
        );

        assertThat(result.getStatus().getCode())
                .isEqualTo(Status.Code.FAILED_PRECONDITION);
    }

    @Test
    void shouldHideUnhandledExceptionDetails() {
        var result = mapper.toStatusException(
                new RuntimeException("database password leaked")
        );

        assertThat(result.getStatus().getCode())
                .isEqualTo(Status.Code.INTERNAL);
        assertThat(result.getStatus().getDescription())
                .isEqualTo("Internal identity service error");
    }
}
