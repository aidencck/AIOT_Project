package com.aiot.home.config;

import com.aiot.common.security.GatewayHeaderAuthInterceptor;
import org.springframework.stereotype.Component;

@Component
public class AuthInterceptor extends GatewayHeaderAuthInterceptor {
}
