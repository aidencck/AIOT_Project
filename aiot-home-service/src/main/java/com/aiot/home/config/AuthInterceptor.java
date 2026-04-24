package com.aiot.home.config;

import com.aiot.common.api.ResultCode;
import com.aiot.common.exception.BusinessException;
import com.aiot.home.utils.JwtUtils;
import com.aiot.home.utils.UserContext;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * JWT 鉴权拦截器
 */
@Slf4j
@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtUtils jwtUtils;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String token = request.getHeader("Authorization");
        
        if (!StringUtils.hasText(token) || !token.startsWith("Bearer ")) {
            log.warn("请求未携带有效Token: {}", request.getRequestURI());
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未携带Token或格式错误");
        }

        token = token.substring(7);
        Claims claims = jwtUtils.parseToken(token);
        
        // 放入上下文
        String userId = claims.getSubject();
        String phone = claims.get("phone", String.class);
        UserContext.set(new UserContext.UserInfo(userId, phone, "Bearer " + token));

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 防止内存泄漏
        UserContext.remove();
    }
}
