package com.aiot.device.config;

import com.aiot.common.security.GatewayHeaderAuthInterceptor;
import org.springframework.stereotype.Component;

@Component
public class AuthInterceptor extends GatewayHeaderAuthInterceptor {
}
