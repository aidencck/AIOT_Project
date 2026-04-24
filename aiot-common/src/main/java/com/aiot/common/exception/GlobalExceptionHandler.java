package com.aiot.common.exception;

import com.aiot.common.api.Result;
import com.aiot.common.api.ResultCode;
import lombok.extern.slf4j.Slf4j;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<Object> handleBusinessException(BusinessException e, HttpServletResponse response) {
        response.setStatus(resolveHttpStatus(e.getResultCode()).value());
        if (e.getResultCode() != null) {
            return Result.fail(e.getResultCode());
        }
        return Result.fail(e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e, HttpServletResponse response) {
        response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        log.error("系统内部异常: ", e);
        return Result.fail(ResultCode.FAILED.getCode(), "系统内部异常，请联系管理员");
    }

    /**
     * 处理参数校验异常
     */
    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public Result<?> handleMethodArgumentNotValidException(org.springframework.web.bind.MethodArgumentNotValidException e,
                                                           HttpServletResponse response) {
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        log.warn("参数校验异常: {}", e.getMessage());
        String message = e.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        return Result.fail(ResultCode.VALIDATE_FAILED.getCode(), message);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<?> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException e,
            HttpServletResponse response) {
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        log.warn("参数类型不匹配: {}", e.getMessage());
        return Result.fail(ResultCode.VALIDATE_FAILED.getCode(), "请求参数类型错误");
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Result<?> handleConstraintViolationException(
            ConstraintViolationException e,
            HttpServletResponse response) {
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        log.warn("参数约束校验异常: {}", e.getMessage());
        String message = e.getConstraintViolations().stream()
                .findFirst()
                .map(ConstraintViolation::getMessage)
                .orElse("请求参数不合法");
        return Result.fail(ResultCode.VALIDATE_FAILED.getCode(), message);
    }

    private HttpStatus resolveHttpStatus(ResultCode resultCode) {
        if (resultCode == null) {
            return HttpStatus.BAD_REQUEST;
        }
        return switch (resultCode) {
            case SUCCESS -> HttpStatus.OK;
            case VALIDATE_FAILED -> HttpStatus.BAD_REQUEST;
            case SHADOW_VERSION_CONFLICT, DEVICE_OFFLINE -> HttpStatus.CONFLICT;
            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case DEVICE_NOT_FOUND, PRODUCT_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case FAILED -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
