package com.aiot.gateway.config;

import com.aiot.common.security.jwt.AiotJwtService;
import com.aiot.common.security.jwt.BearerTokenResolver;
import com.aiot.gateway.security.JwtPreAuthToken;
import com.aiot.gateway.security.JwtReactiveAuthenticationManager;
import com.aiot.gateway.security.ResultServerAccessDeniedHandler;
import com.aiot.gateway.security.ResultServerAuthenticationEntryPoint;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.authentication.ServerAuthenticationFailureHandler;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.util.List;

@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {

    /**
     * 与历史 JwtAuthGlobalFilter 保持一致的白名单。
     */
    public static final List<String> WHITELIST = List.of(
            "/actuator/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/api/v1/emqx/auth",
            "/api/v1/emqx/webhook",
            "/api/v1/users/login",
            "/api/v1/users/register",
            "/api/v1/provision/exchange"
    );

    @Bean
    public ReactiveAuthenticationManager jwtReactiveAuthenticationManager(AiotJwtService jwtService) {
        return new JwtReactiveAuthenticationManager(jwtService);
    }

    @Bean
    public ResultServerAuthenticationEntryPoint resultServerAuthenticationEntryPoint(ObjectMapper objectMapper) {
        return new ResultServerAuthenticationEntryPoint(objectMapper);
    }

    @Bean
    public ResultServerAccessDeniedHandler resultServerAccessDeniedHandler(ObjectMapper objectMapper) {
        return new ResultServerAccessDeniedHandler(objectMapper);
    }

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(org.springframework.security.config.web.server.ServerHttpSecurity http,
                                                           ReactiveAuthenticationManager jwtReactiveAuthenticationManager,
                                                           ResultServerAuthenticationEntryPoint entryPoint,
                                                           ResultServerAccessDeniedHandler accessDeniedHandler) {

        AuthenticationWebFilter jwtAuthFilter = new AuthenticationWebFilter(jwtReactiveAuthenticationManager);
        jwtAuthFilter.setSecurityContextRepository(NoOpServerSecurityContextRepository.getInstance());
        jwtAuthFilter.setRequiresAuthenticationMatcher(nonWhitelistedMatcher());
        jwtAuthFilter.setServerAuthenticationConverter(exchange -> Mono.justOrEmpty(
                        BearerTokenResolver.resolveFromAuthorizationHeader(
                                exchange.getRequest().getHeaders().getFirst(org.springframework.http.HttpHeaders.AUTHORIZATION)))
                .map(JwtPreAuthToken::new));

        // Token 解析/校验失败：直接返回统一 401 JSON（沿用旧提示文本）
        ServerAuthenticationFailureHandler failureHandler = (webFilterExchange, exception) ->
                entryPoint.writeUnauthorizedWithMessage(webFilterExchange.getExchange().getResponse(), exception.getMessage());
        jwtAuthFilter.setAuthenticationFailureHandler(failureHandler);

        return http
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .csrf(csrf -> csrf.disable())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .authorizeExchange(ex -> ex
                        .pathMatchers(WHITELIST.toArray(String[]::new)).permitAll()
                        .anyExchange().authenticated()
                )
                .addFilterAt(jwtAuthFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }

    private ServerWebExchangeMatcher nonWhitelistedMatcher() {
        return exchange -> {
            if (exchange.getRequest().getMethod() == HttpMethod.OPTIONS) {
                return ServerWebExchangeMatcher.MatchResult.notMatch();
            }

            String path = exchange.getRequest().getURI().getPath();
            if (path == null || !StringUtils.hasText(path)) {
                return ServerWebExchangeMatcher.MatchResult.match();
            }

            AntPathMatcher matcher = new AntPathMatcher();
            for (String pattern : WHITELIST) {
                if (pattern != null && matcher.match(pattern, path)) {
                    return ServerWebExchangeMatcher.MatchResult.notMatch();
                }
            }
            return ServerWebExchangeMatcher.MatchResult.match();
        };
    }
}
