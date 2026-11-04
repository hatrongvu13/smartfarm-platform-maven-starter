package com.htv.farmverse.farm.adapter.in.rest;

import com.htv.farmverse.service.web.ApiProblem;
import com.htv.farmverse.service.web.ProblemExceptionHandler;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Lỗi 400 (validation, body sai) và các lỗi MVC khác trả ProblemDetail có {@code code}.
 * Lỗi nghiệp vụ của farm xử lý ngay trong {@link FarmController}.
 *
 * <p>Hai lỗi tranh chấp đồng thời trả 409 thay vì 500:
 * <ul>
 *   <li>Hai request cùng sửa một farm: bản chậm hơn thua khóa lạc quan ({@code @Version}).</li>
 *   <li>Hai request cùng tạo một mã farm: kiểm tra {@code existsByCode} qua được cả hai, ràng buộc
 *       {@code UNIQUE (tenant_id, code)} của DB chặn bản thứ hai.</li>
 * </ul>
 * Thông điệp cố định, không chép lỗi của DB để khỏi lộ tên bảng hay ràng buộc.
 */
@RestControllerAdvice
class FarmExceptionHandler extends ProblemExceptionHandler {

    static final String CONCURRENT_UPDATE = "SF-FARM-010";
    static final String CONFLICT = "SF-FARM-011";

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ApiProblem concurrentUpdate() {
        return ApiProblem.conflict(CONCURRENT_UPDATE, "Farm vừa được người khác sửa, hãy tải lại rồi thử lại");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ApiProblem integrity() {
        return ApiProblem.conflict(CONFLICT, "Dữ liệu xung đột với bản ghi đã có (ví dụ trùng mã farm)");
    }
}
