package com.aiot.home;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * 智能家居服务启动类
 * 负责空间与用户域 (Home, Room, Member) 的核心业务
 */
@OpenAPIDefinition(
        info = @Info(
                title = "AIoT Home Service API",
                version = "v1",
                description = "用户、家庭、房间与成员权限相关接口文档"
        )
)
@SpringBootApplication
@ComponentScan(basePackages = {"com.aiot"}) // 【VibeCoding 强制卡点】确保加载 common 模块组件
@MapperScan("com.aiot.home.mapper")
public class HomeApplication {
    public static void main(String[] args) {
        SpringApplication.run(HomeApplication.class, args);
    }
}
