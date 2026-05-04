package com.aiot.gateway.security;

import com.aiot.common.api.Result;
import com.aiot.common.api.ResultCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    private static final List<String> WHITELIST = List.of(
            "/actuator/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/api/v1/emqx/auth",
            "/api/v1/emqx/webhook",
            "/api/v1/users/login",
            "/api/v1/users/register",
            "/api/v1/provision/exchange"
    );
    private static final String INTERNAL_USER_ID_HEADER = "X-User-Id";
    private static final String INTERNAL_USER_PHONE_HEADER = "X-User-Phone";
    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    @Value("${aiot.security.jwt.secret}")
    private String jwtSecret;
    @Value("${aiot.security.jwt.issuer:}")
    private String jwtIssuer = "";
    @Value("${aiot.security.jwt.audience:}")
    private String jwtAudience = "";
    @Value("${aiot.security.jwt.require-jti:true}")
    private boolean requireJti = true;
    @Value("${aiot.security.jwt.clock-skew-seconds:60}")
    private long clockSkewSeconds = 60L;
    @Value("${aiot.internal.token:}")
    private String internalToken;

    private final ObjectMapper objectMapper;

    public JwtAuthGlobalFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        if (!StringUtils.hasText(jwtSecret) || jwtSecret.length() < 32) {
            throw new IllegalArgumentException("AIOT_JWT_SECRET is required and must be at least 32 characters.");
        }
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (isWhitelisted(path)) {
            return chain.filter(exchange);
        }

        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || authorization.isBlank()) {
            return unauthorized(exchange.getResponse(), "缺少或无效的 Authorization 头");
        }
        if (!authorization.startsWith("Bearer ")) {
            return unauthorized(exchange.getResponse(), "缺少或无效的 Authorization 头");
        }

        String token = authorization.substring("Bearer ".length());
        Claims claims;
        try {
            claims = Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .setAllowedClockSkewSeconds(clockSkewSeconds)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (Exception ex) {
            return unauthorized(exchange.getResponse(), "无效或过期的 Token");
        }
        if (!isClaimsValid(claims)) {
            return unauthorized(exchange.getResponse(), "无效或过期的 Token");
        }

        ServerWebExchange mutated = exchange.mutate()
                .request(builder -> builder.headers(headers -> {
                    headers.remove(INTERNAL_USER_ID_HEADER);
                    headers.remove(INTERNAL_USER_PHONE_HEADER);
                    headers.remove(INTERNAL_TOKEN_HEADER);
                    if (StringUtils.hasText(claims.getSubject())) {
                        headers.set(INTERNAL_USER_ID_HEADER, claims.getSubject());
                    }
                    Object phone = claims.get("phone");
                    if (phone != null) {
                        headers.set(INTERNAL_USER_PHONE_HEADER, String.valueOf(phone));
                    }
                    if (path.startsWith("/api/v1/internal/") && StringUtils.hasText(internalToken)) {
                        headers.set(INTERNAL_TOKEN_HEADER, internalToken);
                    }
                }))
                .build();
        return chain.filter(mutated);
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    private boolean isWhitelisted(String path) {
        if (!StringUtils.hasText(path)) {
            return false;
        }
        for (String pattern : WHITELIST) {
            if (StringUtils.hasText(pattern) && PATH_MATCHER.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    private boolean isClaimsValid(Claims claims) {
        if (!StringUtils.hasText(claims.getSubject())) {
            return false;
        }
        if (requireJti && !StringUtils.hasText(claims.getId())) {
            return false;
        }
        if (StringUtils.hasText(jwtIssuer) && !jwtIssuer.equals(claims.getIssuer())) {
            return false;
        }
        if (StringUtils.hasText(jwtAudience) && !jwtAudience.equals(claims.getAudience())) {
            return false;
        }
        return true;
    }

    private Mono<Void> unauthorized(ServerHttpResponse response, String message) {
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        Result<Object> result = Result.fail(ResultCode.UNAUTHORIZED.getCode(),
                (message == null || message.isBlank()) ? ResultCode.UNAUTHORIZED.getMessage() : message);
        byte[] body = toJsonBytes(result);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }

    private byte[] toJsonBytes(Result<Object> result) {
        try {
            return objectMapper.writeValueAsBytes(result);
        } catch (JsonProcessingException ex) {
            return "{\"code\":401,\"message\":\"未授权\",\"data\":null}".getBytes(StandardCharsets.UTF_8);
        }
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
