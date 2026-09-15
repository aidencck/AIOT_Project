package com.aiot.common.config;

import com.aiot.common.http.TracePropagationExchangeFilterFunction;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 跨服务调用负载均衡 WebClient 构造器：base-url 使用 lb://service-name，
 * 由 Nacos + Spring Cloud LoadBalancer 解析实例列表。
 * 仅在 webflux（WebClient）位于 classpath 时生效，避免纯 MVC 服务因缺少
 * spring-webflux 而无法加载本配置类。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(WebClient.class)
public class LoadBalancedWebClientConfig {

    @Bean
    @LoadBalanced
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder()
                .filter(new TracePropagationExchangeFilterFunction());
    }
}
