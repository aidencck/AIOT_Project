package com.aiot.common.security;

import com.aiot.common.api.ResultCode;
import com.aiot.common.exception.BusinessException;
import com.aiot.common.security.jwt.AiotJwtService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 统一网关透传身份拦截器：
 * - 非 internal 接口：要求 X-User-Id 由网关透传
 * - internal 接口：要求 X-Internal-Token 校验通过
 */
@Slf4j
public class GatewayHeaderAuthInterceptor implements HandlerInterceptor {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String GLOBAL_USER_ID_HEADER = "X-Global-User-Id";
    private static final String USER_PHONE_HEADER = "X-User-Phone";
    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";
    private static final String AUTHORIZATION_HEADER = "Authorization";

    @Value("${aiot.internal.token:}")
    private String internalToken;

    @Value("${aiot.security.allow-direct-user-jwt:false}")
    private boolean allowDirectUserJwt;

    @Autowired(required = false)
    private AiotJwtService jwtService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (request.getRequestURI().startsWith("/api/v1/internal/")) {
            validateInternalToken(request);
            bindUserContextFromHeaders(request);
            return true;
        }

        String userId = request.getHeader(USER_ID_HEADER);
        if (!StringUtils.hasText(userId)) {
            if (tryBindUserContextFromJwt(request)) {
                return true;
            }
            log.warn("请求缺少网关透传用户身份: uri={}", request.getRequestURI());
            throw new BusinessException(ResultCode.UNAUTHORIZED, "缺少用户身份，请通过网关访问");
        }

        String phone = request.getHeader(USER_PHONE_HEADER);
        RequestUserContext.set(buildUserInfo(userId, request.getHeader(GLOBAL_USER_ID_HEADER), phone));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        RequestUserContext.remove();
    }

    private void validateInternalToken(HttpServletRequest request) {
        String providedInternalToken = request.getHeader(INTERNAL_TOKEN_HEADER);
        if (!StringUtils.hasText(internalToken)
                || !StringUtils.hasText(providedInternalToken)
                || !isTokenMatched(internalToken.trim(), providedInternalToken.trim())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "内部调用鉴权失败");
        }
    }

    private boolean isTokenMatched(String configured, String provided) {
        return MessageDigest.isEqual(
                configured.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8)
        );
    }

    private void bindUserContextFromHeaders(HttpServletRequest request) {
        String userId = request.getHeader(USER_ID_HEADER);
        if (!StringUtils.hasText(userId)) {
            return;
        }
        String phone = request.getHeader(USER_PHONE_HEADER);
        RequestUserContext.set(buildUserInfo(userId, request.getHeader(GLOBAL_USER_ID_HEADER), phone));
    }

    private boolean tryBindUserContextFromJwt(HttpServletRequest request) {
        if (!allowDirectUserJwt || jwtService == null) {
            return false;
        }
        String authorizationHeader = request.getHeader(AUTHORIZATION_HEADER);
        if (!StringUtils.hasText(authorizationHeader) || !authorizationHeader.startsWith("Bearer ")) {
            return false;
        }
        Claims claims = jwtService.verify(authorizationHeader.substring(7).trim());
        RequestUserContext.set(buildUserInfo(
                claims.getSubject(),
                claims.get("global_user_id", String.class),
                claims.get("phone", String.class)
        ));
        return true;
    }

    private RequestUserContext.UserInfo buildUserInfo(String userId, String globalUserId, String phone) {
        return new RequestUserContext.UserInfo(
                userId,
                StringUtils.hasText(globalUserId) ? globalUserId : userId,
                phone
        );
    }
}
