package com.aiot.device.config;

import com.aiot.common.api.ResultCode;
import com.aiot.common.exception.BusinessException;
import com.aiot.device.utils.JwtUtils;
import com.aiot.device.utils.UserContext;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtUtils jwtUtils;

    @Value("${aiot.internal.token:}")
    private String internalToken;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (request.getRequestURI().startsWith("/api/v1/internal/")) {
            String providedInternalToken = request.getHeader("X-Internal-Token");
            if (!StringUtils.hasText(internalToken)
                    || !StringUtils.hasText(providedInternalToken)
                    || !internalToken.equals(providedInternalToken)) {
                throw new BusinessException(ResultCode.UNAUTHORIZED, "内部调用鉴权失败");
            }
            return true;
        }

        String token = request.getHeader("Authorization");
        if (!StringUtils.hasText(token) || !token.startsWith("Bearer ")) {
            log.warn("请求未携带有效Token: {}", request.getRequestURI());
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未携带Token或格式错误");
        }

        Claims claims = jwtUtils.parseToken(token.substring(7));
        String userId = claims.getSubject();
        String phone = claims.get("phone", String.class);
        UserContext.set(new UserContext.UserInfo(userId, phone));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.remove();
    }
}
