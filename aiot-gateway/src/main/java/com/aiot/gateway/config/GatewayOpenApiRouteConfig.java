package com.aiot.gateway.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "aiot.openapi.public", name = "enabled", havingValue = "true")
public class GatewayOpenApiRouteConfig {

    @Bean
    public RouteLocator openApiRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("openapi-device", route -> route
                        .path("/v3/api-docs/device")
                        .filters(filter -> filter.rewritePath("/v3/api-docs/device", "/v3/api-docs"))
                        .uri("lb://aiot-device-service"))
                .route("openapi-auth", route -> route
                        .path("/v3/api-docs/auth")
                        .filters(filter -> filter.rewritePath("/v3/api-docs/auth", "/v3/api-docs"))
                        .uri("lb://aiot-auth-service"))
                .route("openapi-home", route -> route
                        .path("/v3/api-docs/home")
                        .filters(filter -> filter.rewritePath("/v3/api-docs/home", "/v3/api-docs"))
                        .uri("lb://aiot-home-service"))
                .route("openapi-rule-engine", route -> route
                        .path("/v3/api-docs/rule-engine")
                        .filters(filter -> filter.rewritePath("/v3/api-docs/rule-engine", "/v3/api-docs"))
                        .uri("lb://aiot-rule-engine"))
                .build();
    }
}
