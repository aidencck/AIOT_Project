package com.aiot.common.api;

public enum ResultCode {
    SUCCESS(200, "操作成功"),
    FAILED(500, "操作失败"),
    VALIDATE_FAILED(400, "参数检验失败"),
    PARAM_MISSING(4001, "缺少必填参数"),
    REQUEST_BODY_INVALID(4002, "请求体格式错误"),
    SHADOW_VERSION_CONFLICT(4091, "设备影子版本冲突"),
    RESOURCE_NOT_FOUND(4040, "资源不存在"),
    METHOD_NOT_ALLOWED(4050, "请求方法不支持"),
    UNAUTHORIZED(401, "暂未登录或token已经过期"),
    FORBIDDEN(403, "没有相关权限"),
    DEVICE_NOT_FOUND(4004, "设备不存在"),
    PRODUCT_NOT_FOUND(4005, "产品不存在"),
    DEVICE_OFFLINE(4006, "设备已离线，无法下发指令");

    private final Integer code;
    private final String message;

    ResultCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

    public Integer getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
