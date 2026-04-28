package com.aiot.home.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 家庭成员角色鉴权注解
 * 用于需要校验用户在指定家庭中角色的接口或方法上。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireHomeRole {

    /**
     * 要求的最小权限角色。
     * 1: Owner (最高)
     * 2: Admin
     * 3: Member (最低)
     */
    int minRole() default 3;
    
    /**
     * 从请求参数中提取 homeId 的参数名，默认是 "homeId"
     */
    String homeIdParam() default "homeId";
}
