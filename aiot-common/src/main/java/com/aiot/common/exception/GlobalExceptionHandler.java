package com.aiot.common.exception;

import com.aiot.common.api.Result;
import com.aiot.common.api.ResultCode;
import com.aiot.common.config.ResponseContractResolver;
import lombok.extern.slf4j.Slf4j;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.validation.BindException;

import java.util.function.Supplier;

/**
 * 全局异常处理
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final ResponseContractResolver responseContractResolver;

    public GlobalExceptionHandler() {
        this(new ResponseContractResolver());
    }

    public GlobalExceptionHandler(ResponseContractResolver responseContractResolver) {
        this.responseContractResolver = responseContractResolver;
    }

    @ExceptionHandler(BusinessException.class)
    public Object handleBusinessException(BusinessException e, HttpServletRequest request, HttpServletResponse response) {
        return protocolAware(request, response, () -> {
            response.setStatus(resolveHttpStatus(e.getResultCode()).value());
            if (e.getResultCode() != null) {
                return Result.fail(e.getResultCode());
            }
            return Result.fail(e.getMessage());
        });
    }

    @ExceptionHandler(Exception.class)
    public Object handleException(Exception e, HttpServletRequest request, HttpServletResponse response) {
        return protocolAware(request, response, () -> {
            response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
            log.error("系统内部异常: ", e);
            return Result.fail(ResultCode.FAILED.getCode(), "系统内部异常，请联系管理员");
        });
    }

    /**
     * 处理参数校验异常
     */
    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public Object handleMethodArgumentNotValidException(org.springframework.web.bind.MethodArgumentNotValidException e,
                                                        HttpServletRequest request,
                                                        HttpServletResponse response) {
        return protocolAware(request, response, () -> {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            log.warn("参数校验异常: {}", e.getMessage());
            String message = e.getBindingResult().getAllErrors().get(0).getDefaultMessage();
            return Result.fail(ResultCode.VALIDATE_FAILED.getCode(), message);
        });
    }

    @ExceptionHandler(BindException.class)
    public Object handleBindException(BindException e,
                                      HttpServletRequest request,
                                      HttpServletResponse response) {
        return protocolAware(request, response, () -> {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            log.warn("参数绑定/校验异常: {}", e.getMessage());
            String message = e.getBindingResult().getAllErrors().stream()
                    .findFirst()
                    .map(err -> err.getDefaultMessage() == null ? "请求参数不合法" : err.getDefaultMessage())
                    .orElse("请求参数不合法");
            return Result.fail(ResultCode.VALIDATE_FAILED.getCode(), message);
        });
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Object handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException e,
            HttpServletRequest request,
            HttpServletResponse response) {
        return protocolAware(request, response, () -> {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            log.warn("参数类型不匹配: {}", e.getMessage());
            return Result.fail(ResultCode.VALIDATE_FAILED.getCode(), "请求参数类型错误");
        });
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Object handleMissingServletRequestParameterException(
            MissingServletRequestParameterException e,
            HttpServletRequest request,
            HttpServletResponse response) {
        return protocolAware(request, response, () -> {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            log.warn("缺少请求参数: {}", e.getMessage());
            return Result.fail(ResultCode.PARAM_MISSING.getCode(), "缺少必填参数: " + e.getParameterName());
        });
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Object handleHttpMessageNotReadableException(
            HttpMessageNotReadableException e,
            HttpServletRequest request,
            HttpServletResponse response) {
        return protocolAware(request, response, () -> {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            log.warn("请求体不可读: {}", e.getMessage());
            return Result.fail(ResultCode.REQUEST_BODY_INVALID.getCode(), "请求体格式错误或字段类型不匹配");
        });
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Object handleHttpRequestMethodNotSupportedException(
            HttpRequestMethodNotSupportedException e,
            HttpServletRequest request,
            HttpServletResponse response) {
        return protocolAware(request, response, () -> {
            response.setStatus(HttpStatus.METHOD_NOT_ALLOWED.value());
            log.warn("请求方法不支持: {}", e.getMessage());
            return Result.fail(ResultCode.METHOD_NOT_ALLOWED.getCode(), "请求方法不支持: " + e.getMethod());
        });
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public Object handleNoHandlerFoundException(NoHandlerFoundException e,
                                                HttpServletRequest request,
                                                HttpServletResponse response) {
        return protocolAware(request, response, () -> {
            response.setStatus(HttpStatus.NOT_FOUND.value());
            log.warn("资源不存在: {}", e.getRequestURL());
            return Result.fail(ResultCode.RESOURCE_NOT_FOUND.getCode(), "资源不存在: " + e.getRequestURL());
        });
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Object handleConstraintViolationException(
            ConstraintViolationException e,
            HttpServletRequest request,
            HttpServletResponse response) {
        return protocolAware(request, response, () -> {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            log.warn("参数约束校验异常: {}", e.getMessage());
            String message = e.getConstraintViolations().stream()
                    .findFirst()
                    .map(ConstraintViolation::getMessage)
                    .orElse("请求参数不合法");
            return Result.fail(ResultCode.VALIDATE_FAILED.getCode(), message);
        });
    }

    @ExceptionHandler(ResponseStatusException.class)
    public Object handleResponseStatusException(ResponseStatusException e,
                                                HttpServletRequest request,
                                                HttpServletResponse response) {
        return protocolAware(request, response, () -> {
            HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
            if (status == null) {
                status = HttpStatus.INTERNAL_SERVER_ERROR;
            }
            response.setStatus(status.value());
            if (status.is5xxServerError()) {
                log.error("ResponseStatusException: {}", e.getMessage(), e);
                return Result.fail(ResultCode.FAILED.getCode(), "系统内部异常，请联系管理员");
            }
            log.warn("ResponseStatusException: {}", e.getMessage());
            String reason = e.getReason();
            String message = (reason == null || reason.isBlank()) ? status.getReasonPhrase() : reason;
            return Result.fail(resolveResultCode(status).getCode(), message);
        });
    }

    private HttpStatus resolveHttpStatus(ResultCode resultCode) {
        if (resultCode == null) {
            return HttpStatus.BAD_REQUEST;
        }
        return switch (resultCode) {
            case SUCCESS -> HttpStatus.OK;
            case VALIDATE_FAILED, PARAM_MISSING, REQUEST_BODY_INVALID -> HttpStatus.BAD_REQUEST;
            case SHADOW_VERSION_CONFLICT, DEVICE_OFFLINE -> HttpStatus.CONFLICT;
            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case RESOURCE_NOT_FOUND, DEVICE_NOT_FOUND, PRODUCT_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case METHOD_NOT_ALLOWED -> HttpStatus.METHOD_NOT_ALLOWED;
            case FAILED -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    private ResultCode resolveResultCode(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> ResultCode.VALIDATE_FAILED;
            case UNAUTHORIZED -> ResultCode.UNAUTHORIZED;
            case FORBIDDEN -> ResultCode.FORBIDDEN;
            case NOT_FOUND -> ResultCode.RESOURCE_NOT_FOUND;
            case METHOD_NOT_ALLOWED -> ResultCode.METHOD_NOT_ALLOWED;
            default -> ResultCode.FAILED;
        };
    }

    private Object protocolAware(HttpServletRequest request, HttpServletResponse response, Supplier<Object> standardHandler) {
        if (responseContractResolver.shouldUseProtocolFallback(request)) {
            log.warn("协议接口异常回退，path={}", request == null ? "null" : request.getRequestURI());
            return protocolFallback(response);
        }
        return standardHandler.get();
    }

    private String protocolFallback(HttpServletResponse response) {
        response.setStatus(HttpStatus.OK.value());
        response.setContentType(MediaType.TEXT_PLAIN_VALUE);
        return "deny";
    }
}
