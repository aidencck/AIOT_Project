package com.aiot.gateway.security;

import com.aiot.common.security.jwt.AiotJwtService;
import com.aiot.common.security.jwt.BearerTokenResolver;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 将 JWT 解析出的可信用户信息，透传为下游服务认可的 Header。
 *
 * <p>注意：本过滤器直接复用 aiot-common 的 JWT 校验能力自行解析 Authorization，
 * 而非依赖 Spring Security 的 ReactiveSecurityContextHolder。原因是网关配置了
 * NoOpServerSecurityContextRepository，鉴权成功后 SecurityContext 不会传递到
 * GlobalFilter（GlobalFilter 阶段 exchange.getPrincipal() 恒为空），
 * 导致下游拿不到 X-User-Id 而统一返回 401。</p>
 */
@Component
public class GatewayUserHeaderGlobalFilter implements GlobalFilter, Ordered {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String GLOBAL_USER_ID_HEADER = "X-Global-User-Id";
    private static final String USER_PHONE_HEADER = "X-User-Phone";
    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";
    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final Pattern TRACE_ID_PATTERN = Pattern.compile("[A-Za-z0-9._:-]{1,64}");

    private final AiotJwtService jwtService;

    @Value("${aiot.internal.token:}")
    private String internalToken;

    public GatewayUserHeaderGlobalFilter(AiotJwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        String traceId = resolveTraceId(exchange);
        exchange.getResponse().getHeaders().set(TRACE_ID_HEADER, traceId);
        AiotJwtPrincipal principal = resolvePrincipal(exchange);

        ServerWebExchange mutated = exchange.mutate()
                .request(builder -> builder.headers(headers -> {
                    // 始终清除伪造的身份头，防止前端/外部调用越权
                    headers.remove(USER_ID_HEADER);
                    headers.remove(GLOBAL_USER_ID_HEADER);
                    headers.remove(USER_PHONE_HEADER);
                    headers.remove(INTERNAL_TOKEN_HEADER);

                    // 透传统一链路 traceId
                    headers.set(TRACE_ID_HEADER, traceId);

                    if (principal != null && StringUtils.hasText(principal.userId())) {
                        headers.set(USER_ID_HEADER, principal.userId());
                    }
                    if (principal != null && StringUtils.hasText(principal.globalUserId())) {
                        headers.set(GLOBAL_USER_ID_HEADER, principal.globalUserId());
                    }
                    if (principal != null && StringUtils.hasText(principal.phone())) {
                        headers.set(USER_PHONE_HEADER, principal.phone());
                    }
                    if (path != null
                            && path.startsWith("/api/v1/internal/")
                            && StringUtils.hasText(internalToken)) {
                        headers.set(INTERNAL_TOKEN_HEADER, internalToken);
                    }
                }))
                .build();

        return chain.filter(mutated);
    }

    private String resolveTraceId(ServerWebExchange exchange) {
        String incomingTraceId = exchange.getRequest().getHeaders().getFirst(TRACE_ID_HEADER);
        // 仅接受限定字符集与长度的 traceId，防止外部注入控制字符/超长值污染下游日志与链路。
        if (isValidTraceId(incomingTraceId)) {
            return incomingTraceId;
        }
        return UUID.randomUUID().toString().replace("-", "");
    }

    private boolean isValidTraceId(String traceId) {
        return StringUtils.hasText(traceId) && TRACE_ID_PATTERN.matcher(traceId).matches();
    }

    private AiotJwtPrincipal resolvePrincipal(ServerWebExchange exchange) {
        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        String token = BearerTokenResolver.resolveFromAuthorizationHeader(authorization);
        if (!StringUtils.hasText(token)) {
            return null;
        }
        try {
            Claims claims = jwtService.verify(token);
            Object phone = claims.get("phone");
            Object globalUserId = claims.get("global_user_id");
            String subject = claims.getSubject();
            return new AiotJwtPrincipal(
                    subject,
                    globalUserId == null ? subject : String.valueOf(globalUserId),
                    phone == null ? null : String.valueOf(phone));
        } catch (Exception ex) {
            // token 无效/过期：交由 Security 层统一拦截并返回 401，这里仅不下发身份头
            return null;
        }
    }

    @Override
    public int getOrder() {
        // 早于路由，且在去除伪造 Header 的 default-filters 之前注入可信身份
        return -100;
    }
}
