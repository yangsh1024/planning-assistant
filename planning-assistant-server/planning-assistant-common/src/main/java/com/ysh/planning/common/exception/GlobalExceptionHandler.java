package com.ysh.planning.common.exception;

import com.ysh.planning.common.response.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;

/** 将业务异常、参数校验异常和未预期异常转换为统一 REST 响应。 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 按业务错误码映射 HTTP 状态并返回受控失败响应。 */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<Void>> handleBiz(BizException e) {
        log.warn("business_failure error_code={}", e.getCode());
        HttpStatus status = switch (e.getCode()) {
            case 400 -> HttpStatus.BAD_REQUEST;
            case 401 -> HttpStatus.UNAUTHORIZED;
            case 403 -> HttpStatus.FORBIDDEN;
            case 404 -> HttpStatus.NOT_FOUND;
            case 409 -> HttpStatus.CONFLICT;
            case 429 -> HttpStatus.TOO_MANY_REQUESTS;
            case 503 -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return ResponseEntity.status(status).body(Result.fail(e.getCode(), e.getMessage()));
    }

    /** 汇总请求体字段校验错误。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("validation_failure field_count={}", e.getBindingResult().getFieldErrorCount());
        return ResponseEntity.badRequest().body(Result.fail(400, message));
    }

    /** 处理路径和查询参数的约束校验错误。 */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraintViolation(ConstraintViolationException e) {
        log.warn("constraint_violation violation_count={}", e.getConstraintViolations().size());
        return ResponseEntity.badRequest().body(Result.fail(400, "请求参数不合法"));
    }

    /** 记录未处理异常，并隐藏内部错误细节。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleGeneral(Exception e) {
        log.error("unhandled_exception type={}", e.getClass().getSimpleName(), e);
        return ResponseEntity.internalServerError()
                .body(Result.fail(500, "服务内部错误"));
    }
}
