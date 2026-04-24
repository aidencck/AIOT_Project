package com.aiot.auth;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;

@OpenAPIDefinition(
        info = @Info(
                title = "AIoT Auth Service API",
                version = "v1",
                description = "设备鉴权与EMQX回调相关接口文档"
        )
)
@SpringBootApplication
@EnableDiscoveryClient
@ComponentScan(basePackages = {"com.aiot"})
@MapperScan("com.aiot.auth.mapper")
public class AuthApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }
}
