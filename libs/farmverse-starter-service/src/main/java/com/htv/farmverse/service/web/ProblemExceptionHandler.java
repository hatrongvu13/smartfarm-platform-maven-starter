package com.htv.farmverse.service.web;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Mọi lỗi của Spring MVC (body sai, thiếu tham số, sai method...) trả ProblemDetail có field {@code code},
 * cùng dạng với {@link ApiProblem}. Dùng cơ chế chuẩn {@link ResponseEntityExceptionHandler}: chỉ bổ sung
 * {@code code} và danh sách lỗi theo field.
 *
 * <ul>
 *   <li>Dữ liệu không hợp lệ ({@code @Valid}): 400, code {@value #VALIDATION}, kèm {@code errors: [{field, message}]}.</li>
 *   <li>Lỗi MVC khác: code {@code SF-HTTP-<status>} (ví dụ {@code SF-HTTP-405}).</li>
 *   <li>Lỗi đã có {@code code} (ném {@link ApiProblem}) giữ nguyên.</li>
 * </ul>
 *
 * <p><b>Không</b> ghi lại giá trị người dùng đã gửi, vì có thể là mật khẩu hay mã TOTP.
 *
 * <p>Lớp trừu tượng, không có auto-configuration: mỗi service tạo một lớp con gắn
 * {@code @RestControllerAdvice}. Lỗi bảo mật 401/403 không đi qua đây, xem {@link ProblemResponses}.
 */
public abstract class ProblemExceptionHandler extends ResponseEntityExceptionHandler {

    public static final String VALIDATION = "SF-VALIDATION-001";
    public static final String HTTP_PREFIX = "SF-HTTP-";
    public static final String ERRORS = "errors";

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem) {
            enrich(problem, ex, statusCode);
        }
        return response;
    }

    private static void enrich(ProblemDetail problem, Exception ex, HttpStatusCode status) {
        Map<String, Object> properties = problem.getProperties();
        if (properties != null && properties.containsKey(ApiProblem.CODE)) {
            return;
        }
        List<Map<String, String>> errors = null;
        String code = HTTP_PREFIX + status.value();
        if (ex instanceof MethodArgumentNotValidException invalid) {
            errors = fieldErrors(invalid.getBindingResult());
        } else if (ex instanceof HandlerMethodValidationException invalid) {
            errors = messages(invalid.getAllErrors());
        }
        if (errors != null) {
            code = VALIDATION;
            problem.setDetail("Dữ liệu gửi lên không hợp lệ");
            problem.setProperty(ERRORS, errors);
        }
        problem.setType(URI.create("urn:farmverse:problem:" + code));
        problem.setProperty(ApiProblem.CODE, code);
    }

    private static List<Map<String, String>> fieldErrors(BindingResult result) {
        List<Map<String, String>> errors = new ArrayList<>();
        for (FieldError error : result.getFieldErrors()) {
            errors.add(error(error.getField(), error.getDefaultMessage()));
        }
        for (ObjectError error : result.getGlobalErrors()) {
            errors.add(error(error.getObjectName(), error.getDefaultMessage()));
        }
        return errors;
    }

    private static List<Map<String, String>> messages(List<? extends MessageSourceResolvable> resolvables) {
        List<Map<String, String>> errors = new ArrayList<>();
        for (MessageSourceResolvable resolvable : resolvables) {
            String field = resolvable instanceof FieldError fieldError ? fieldError.getField() : "-";
            errors.add(error(field, resolvable.getDefaultMessage()));
        }
        return errors;
    }

    private static Map<String, String> error(String field, String message) {
        Map<String, String> error = new LinkedHashMap<>();
        error.put("field", field);
        error.put("message", message == null ? "không hợp lệ" : message);
        return error;
    }
}
