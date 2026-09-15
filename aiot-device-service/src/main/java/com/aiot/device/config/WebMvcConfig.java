package com.aiot.device.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Objects;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final @NonNull AuthInterceptor authInterceptor;

    public WebMvcConfig(@NonNull AuthInterceptor authInterceptor) {
        this.authInterceptor = Objects.requireNonNull(authInterceptor, "authInterceptor must not be null");
    }

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                // 设备端换取密钥无需携带用户JWT
                .excludePathPatterns("/api/v1/provision/exchange");
    }
}
