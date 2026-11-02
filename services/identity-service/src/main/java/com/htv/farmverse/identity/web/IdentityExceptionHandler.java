package com.htv.farmverse.identity.web;

import com.htv.farmverse.service.web.ProblemExceptionHandler;

import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Lỗi 400 (validation, body sai) và các lỗi MVC khác của REST quản trị trả ProblemDetail có {@code code}.
 * Logic nằm ở {@link ProblemExceptionHandler} dùng chung.
 */
@RestControllerAdvice
class IdentityExceptionHandler extends ProblemExceptionHandler {
}
