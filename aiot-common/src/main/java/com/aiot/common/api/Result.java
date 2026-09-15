package com.aiot.common.api;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "统一响应包装结构")
public class Result<T> {
    @Schema(description = "业务状态码，200 表示成功", example = "200")
    private Integer code;
    @Schema(description = "提示信息", example = "操作成功")
    private String message;
    @Schema(description = "业务数据负载")
    private T data;

    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(ResultCode.SUCCESS.getCode());
        result.setMessage(ResultCode.SUCCESS.getMessage());
        result.setData(data);
        return result;
    }

    public static <T> Result<T> fail(Integer code, String message) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMessage(message);
        return result;
    }

    public static <T> Result<T> fail(ResultCode errorCode) {
        Result<T> result = new Result<>();
        result.setCode(errorCode.getCode());
        result.setMessage(errorCode.getMessage());
        return result;
    }
    
    public static <T> Result<T> fail(String message) {
        Result<T> result = new Result<>();
        result.setCode(ResultCode.FAILED.getCode());
        result.setMessage(message);
        return result;
    }
}
