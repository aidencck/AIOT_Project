package com.aiot.common.config;

import com.aiot.common.http.TracePropagationRequestInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * 为跨服务调用提供开启服务负载均衡能力的 RestClient.Builder：baseUrl 使用 lb://service-name 格式，
 * 服务负载均衡指Spring Cloud LoadBalancer会自动从Nacos获取该服务名对应的所有可用实例列表，
 * 按照内置的负载策略（如轮询、随机等）选择一个实例进行调用，避免请求集中落在单个实例上，实现集群流量分摊；
 * 同时本配置会为所有请求透传链路追踪信息，保证分布式调用链的监控完整性。
 * 仅在spring-web包中的RestClient类存在于类路径时，该配置才会生效。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(RestClient.class)
public class LoadBalancedRestClientConfig {

    @Bean
    @LoadBalanced
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder()
                .requestInterceptor(new TracePropagationRequestInterceptor());
    }
}
