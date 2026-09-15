package com.aiot.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

@Configuration
public class GatewayRateLimitConfig {

    /**
     * 默认 KeyResolver（IP 维度）。标记 @Primary 以满足
     * {@code RequestRateLimiterGatewayFilterFactory} 对「单一默认 KeyResolver」的注入要求；
     * 路由中仍可用 {@code #{@ipKeyResolver}} 显式引用。
     *
     * <p>限流维度固定为「客户端真实 IP」，不再混入 X-User-Id 或请求路径：
     * 混入用户维度会让同 IP 下多账号各自独立限流，绕过「单 IP 独立限制」；
     * 混入路径会让同一路由下不同路径各自持有令牌桶，偏离路由级限流语义。</p>
     */
    @Bean
    @Primary
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.just(resolveClientIp(exchange));
    }

    /**
     * EMQX 服务器回调（/api/v1/emqx/webhook、/api/v1/emqx/auth）专用限流维度。
     *
     * <p>这些回调由 EMQX 以服务器身份发起，不携带 X-User-Id。若沿用 ipKeyResolver，
     * 会退化为按来源 IP，把所有设备的上下线风暴塌缩进同一个单 IP 令牌桶，导致合法
     * 流量被 429 误伤。这里改用「EMQX 路由前缀」作为全局维度，配合宽松保护性阈值，
     * 仅用于防止 EMQX 配置错误/异常时的风暴打垮后端；真正的准入安全由 auth-service 的
     * HMAC 签名 + 时间戳 + Redis 防重放三重校验兜底。</p>
     */
    @Bean
    public KeyResolver pathKeyResolver() {
        return exchange -> Mono.just(resolvePathKey(exchange));
    }

    private String resolveClientIp(org.springframework.web.server.ServerWebExchange exchange) {
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        if (remoteAddress != null && remoteAddress.getAddress() != null) {
            return remoteAddress.getAddress().getHostAddress();
        }
        return "unknown";
    }

    private String resolvePathKey(org.springframework.web.server.ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().value();
        // 固定为 EMQX 路由前缀，防止攻击者构造任意子路径制造高基数 Key 绕过限流。
        if (path.equals("/api/v1/emqx") || path.startsWith("/api/v1/emqx/")) {
            return "/api/v1/emqx";
        }
        return path;
    }
}
