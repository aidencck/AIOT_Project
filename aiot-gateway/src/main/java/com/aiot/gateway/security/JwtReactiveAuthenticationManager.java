package com.aiot.gateway.security;

import com.aiot.common.exception.BusinessException;
import com.aiot.common.security.jwt.AiotJwtService;
import io.jsonwebtoken.Claims;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 网关 JWT 鉴权：复用 aiot-common 的统一 JWT 校验能力。
 */
public class JwtReactiveAuthenticationManager implements ReactiveAuthenticationManager {

    private final AiotJwtService jwtService;

    public JwtReactiveAuthenticationManager(AiotJwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        String token = null;
        if (authentication instanceof JwtPreAuthToken preAuth) {
            token = (String) preAuth.getCredentials();
        } else if (authentication != null && authentication.getCredentials() instanceof String s) {
            token = s;
        }

        if (!StringUtils.hasText(token)) {
            return Mono.error(new BadCredentialsException("缺少或无效的 Authorization 头"));
        }

        String resolvedToken = token;
        return Mono.fromCallable(() -> jwtService.verify(resolvedToken))
                .onErrorMap(BusinessException.class,
                        ex -> new BadCredentialsException(ex.getMessage(), ex))
                .map(claims -> toAuthentication(resolvedToken, claims));
    }

    private Authentication toAuthentication(String token, Claims claims) {
        Object phone = claims.get("phone");
        AiotJwtPrincipal principal = new AiotJwtPrincipal(claims.getSubject(),
                phone == null ? null : String.valueOf(phone));

        // 网关本身不做细粒度权限控制：Authorities 为空即可满足 authenticated
        return new UsernamePasswordAuthenticationToken(principal, token, List.of());
    }
}
