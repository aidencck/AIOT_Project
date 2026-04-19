package com.aiot.common.api;

public enum ResultCode {
    SUCCESS(200, "操作成功"),
    FAILED(500, "操作失败"),
    VALIDATE_FAILED(400, "参数检验失败"),
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
