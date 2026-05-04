package com.aiot.device.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 设备域资源授权注解
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireHomePermission {

    int minRole() default 3;

    String denyMessage() default "家庭权限不足";

    /**
     * 直接从请求中提取 homeId 的参数名（Path/Query/Body）
     */
    String homeIdParam() default "homeId";

    /**
     * 资源 ID 参数名（如 deviceId/taskId），与 resourceType 配合使用
     */
    String resourceIdParam() default "";

    ResourceType resourceType() default ResourceType.NONE;
}
