package com.aiot.home;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 智能家居服务启动类
 * 负责空间与用户域 (Home, Room, Member) 的核心业务
 * 
 * 注解核心框架梳理：
 * 1. @OpenAPIDefinition：Swagger3 API文档核心注解
 *    - 作用：生成接口文档基础配置
 *    - 解决问题：统一管理API元信息，自动生成可视化接口文档
 *    - 配置内容：title(文档标题)、version(API版本)、description(接口描述)
 * 
 * 2. @SpringBootApplication：SpringBoot启动核心注解
 *    - 组合注解：整合@Configuration、@EnableAutoConfiguration、@ComponentScan
 *    - 解决问题：自动配置Spring上下文，开启组件扫描与自动配置
 *    - 核心逻辑：自动加载classpath下的Spring组件，自动装配依赖
 * 
 * 3. @EnableScheduling：定时任务开启注解
 *    - 作用：启用Spring的定时任务调度功能
 *    - 解决问题：支持@Scheduled注解的定时任务执行
 *    - 底层逻辑：自动注册TaskScheduler，开启任务调度线程池
 * 
 * 4. @ComponentScan：组件扫描路径配置
 *    - 作用：指定Spring扫描@Component等衍生注解的包路径
 *    - 解决问题：加载跨模块的通用组件（common包）与当前模块组件
 *    - 配置内容：basePackages指定扫描的包集合
 * 
 * 5. @MapperScan：MyBatis Mapper扫描注解
 *    - 作用：扫描指定包下的MyBatis Mapper接口，自动生成代理实现类
 *    - 解决问题：无需手动注册每个Mapper，自动注入到Spring容器
 *    - 配置内容：指定Mapper接口所在的包路径
 */
@OpenAPIDefinition(
        info = @Info(
                title = "AIoT Home Service API",
                version = "v1",
                description = "用户、家庭、房间与成员权限相关接口文档"
        )
)
@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = {"com.aiot.home", "com.aiot.common"})
@MapperScan("com.aiot.home.repository")
public class HomeApplication {
    public static void main(String[] args) {
        SpringApplication.run(HomeApplication.class, args);
    }
}
