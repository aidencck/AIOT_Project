package com.aiot.gateway.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 将 Spring Security 鉴权后的用户信息，透传为下游服务认可的 Header。
 *
 * 保持旧行为：
 * - 去除伪造的 X-User-Id/X-User-Phone/X-Internal-Token
 * - 注入可信的 X-User-Id/X-User-Phone
 * - /api/v1/internal/** 额外注入 X-Internal-Token（如已配置）
 */
@Component
public class GatewayUserHeaderGlobalFilter implements GlobalFilter, Ordered {

    private static final String INTERNAL_USER_ID_HEADER = "X-User-Id";
    private static final String INTERNAL_USER_PHONE_HEADER = "X-User-Phone";
    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    @Value("${aiot.internal.token:}")
    private String internalToken;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        Mono<Authentication> authMono = exchange.getPrincipal()
                .ofType(Authentication.class)
                .switchIfEmpty(ReactiveSecurityContextHolder.getContext().map(SecurityContext::getAuthentication));

        return authMono
                .flatMap(auth -> {
                    ServerWebExchange mutated = exchange.mutate()
                            .request(builder -> builder.headers(headers -> {
                                headers.remove(INTERNAL_USER_ID_HEADER);
                                headers.remove(INTERNAL_USER_PHONE_HEADER);
                                headers.remove(INTERNAL_TOKEN_HEADER);

                                AiotJwtPrincipal principal = extractPrincipal(auth);
                                if (principal != null && StringUtils.hasText(principal.userId())) {
                                    headers.set(INTERNAL_USER_ID_HEADER, principal.userId());
                                }
                                if (principal != null && StringUtils.hasText(principal.phone())) {
                                    headers.set(INTERNAL_USER_PHONE_HEADER, principal.phone());
                                }
                                if (path != null
                                        && path.startsWith("/api/v1/internal/")
                                        && StringUtils.hasText(internalToken)) {
                                    headers.set(INTERNAL_TOKEN_HEADER, internalToken);
                                }
                            }))
                            .build();
                    return chain.filter(mutated);
                })
                // 白名单/匿名请求也需要清理伪造 Header（保持防护能力）
                .switchIfEmpty(Mono.defer(() -> {
                    ServerWebExchange mutated = exchange.mutate()
                            .request(builder -> builder.headers(headers -> {
                                headers.remove(INTERNAL_USER_ID_HEADER);
                                headers.remove(INTERNAL_USER_PHONE_HEADER);
                                headers.remove(INTERNAL_TOKEN_HEADER);
                                if (path != null
                                        && path.startsWith("/api/v1/internal/")
                                        && StringUtils.hasText(internalToken)) {
                                    headers.set(INTERNAL_TOKEN_HEADER, internalToken);
                                }
                            }))
                            .build();
                    return chain.filter(mutated);
                }));
    }

    private AiotJwtPrincipal extractPrincipal(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof AiotJwtPrincipal p) {
            return p;
        }
        return null;
    }

    @Override
    public int getOrder() {
        // 早于大部分业务 Filter 注入身份头，且在路由前生效
        return -100;
    }
}
