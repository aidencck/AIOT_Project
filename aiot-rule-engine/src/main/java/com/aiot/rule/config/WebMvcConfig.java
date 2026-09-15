package com.aiot.rule.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Objects;

/**
 * rule-engine WebMVC 配置：注册统一鉴权拦截器，覆盖所有 /api/** 路径。
 * 注意：rule-engine 无任何需要放行的对外路径，故不配置 excludePathPatterns。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final @NonNull AuthInterceptor authInterceptor;

    public WebMvcConfig(@NonNull AuthInterceptor authInterceptor) {
        this.authInterceptor = Objects.requireNonNull(authInterceptor, "authInterceptor must not be null");
    }

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**");
    }
}
