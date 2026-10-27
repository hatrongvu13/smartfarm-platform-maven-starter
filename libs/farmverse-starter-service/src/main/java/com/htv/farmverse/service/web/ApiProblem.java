package com.htv.farmverse.service.web;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * Lỗi nghiệp vụ trả về dạng RFC 9457 ProblemDetail, có thêm field {@code code}
 * theo định dạng {@code SF-<MIEN>-<SO>}. Spring MVC tự render, không cần advice riêng.
 */
public class ApiProblem extends ErrorResponseException {

    public static final String CODE = "code";

    public ApiProblem(HttpStatus status, String code, String detail) {
        super(status, problem(status, code, detail), null);
    }

    public static ApiProblem notFound(String code, String detail) {
        return new ApiProblem(HttpStatus.NOT_FOUND, code, detail);
    }

    public static ApiProblem conflict(String code, String detail) {
        return new ApiProblem(HttpStatus.CONFLICT, code, detail);
    }

    public static ApiProblem badRequest(String code, String detail) {
        return new ApiProblem(HttpStatus.BAD_REQUEST, code, detail);
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("urn:farmverse:problem:" + code));
        problem.setProperty(CODE, code);
        return problem;
    }

    public String code() {
        Object properties = getBody().getProperties() == null ? null : getBody().getProperties().get(CODE);
        return properties == null ? null : properties.toString();
    }
}
