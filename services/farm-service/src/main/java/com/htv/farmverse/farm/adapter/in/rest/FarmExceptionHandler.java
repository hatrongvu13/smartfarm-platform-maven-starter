package com.htv.farmverse.farm.adapter.in.rest;

import com.htv.farmverse.service.web.ProblemExceptionHandler;

import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Lỗi 400 (validation, body sai) và các lỗi MVC khác trả ProblemDetail có {@code code}.
 * Lỗi nghiệp vụ của farm xử lý ngay trong {@link FarmController}.
 */
@RestControllerAdvice
class FarmExceptionHandler extends ProblemExceptionHandler {
}
