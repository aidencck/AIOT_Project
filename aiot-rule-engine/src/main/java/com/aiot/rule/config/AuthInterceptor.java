package com.aiot.rule.config;

import com.aiot.common.security.GatewayHeaderAuthInterceptor;
import org.springframework.stereotype.Component;

/**
 * rule-engine 统一鉴权拦截器：
 * 复用 aiot-common 的网关透传身份拦截逻辑（非 internal 接口校验 X-User-Id，internal 接口校验 X-Internal-Token）。
 */
@Component
public class AuthInterceptor extends GatewayHeaderAuthInterceptor {
}
