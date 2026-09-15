package com.aiot.common.config;

import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 统一 OpenAPI 鉴权方案：注册 Bearer JWT SecurityScheme，供各服务 Swagger UI 填写 Token 调试受保护接口。
 * 不覆盖各服务的 @OpenAPIDefinition（title/version/description 由服务自身定义），仅补齐安全组件。
 * 通过 @ConditionalOnClass 保证无 springdoc 依赖的模块（如 gateway 之外的纯内部模块）安全跳过。
 */
@Configuration
@ConditionalOnClass(name = "org.springdoc.core.customizers.OpenApiCustomizer")
public class OpenApiConfig {

    public static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenApiCustomizer bearerSecurityCustomizer() {
        return openApi -> openApi.getComponents()
                .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("使用登录接口 /api/v1/users/login 获取的 JWT Token"));
    }

    /**
     * 供需要鉴权的接口在 @Operation(security = @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME))
     * 中引用，避免魔法字符串散落。
     */
    public static SecurityRequirement bearerRequirement() {
        return new SecurityRequirement().addList(SECURITY_SCHEME_NAME);
    }
}
