package com.htv.smartfarm.common.exception;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class BusinessExceptionTest {
    @Test
    void exposesCodeAndMessage() {
        var ex = new ConflictException("EMAIL_EXISTS", "Email exists");
        assertEquals("EMAIL_EXISTS", ex.code());
        assertEquals("Email exists", ex.getMessage());
    }

    @Test
    void createsEntityNotFound() {
        var ex = NotFoundException.entity("User", "user-1");
        assertEquals("USER_NOT_FOUND", ex.code());
    }
}
