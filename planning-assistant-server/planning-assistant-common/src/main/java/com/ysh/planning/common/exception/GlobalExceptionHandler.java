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

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<Void>> handleBiz(BizException e) {
        log.warn("Business exception: code={}, message={}", e.getCode(), e.getMessage());
        HttpStatus status = switch (e.getCode()) {
            case 400 -> HttpStatus.BAD_REQUEST;
            case 401 -> HttpStatus.UNAUTHORIZED;
            case 403 -> HttpStatus.FORBIDDEN;
            case 404 -> HttpStatus.NOT_FOUND;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return ResponseEntity.status(status).body(Result.fail(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("Validation exception: {}", message);
        return ResponseEntity.badRequest().body(Result.fail(400, message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraintViolation(ConstraintViolationException e) {
        log.warn("Constraint violation: {}", e.getMessage());
        return ResponseEntity.badRequest().body(Result.fail(400, "请求参数不合法"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleGeneral(Exception e) {
        log.error("Unexpected exception", e);
        return ResponseEntity.internalServerError()
                .body(Result.fail(500, "服务内部错误"));
    }
}
