package com.aiot.home.aspect;

import com.aiot.common.api.ResultCode;
import com.aiot.common.exception.BusinessException;
import com.aiot.home.annotation.RequireHomeRole;
import com.aiot.home.service.HomeCacheManager;
import com.aiot.home.utils.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerMapping;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * 权限校验切面
 */
@Slf4j
@Aspect
@Component
@Order(10) // 确保在事务切面之前执行
public class RequireHomeRoleAspect {

    @Autowired
    private HomeCacheManager homeCacheManager;

    @Before("@annotation(requireHomeRole)")
    public void doBefore(JoinPoint joinPoint, RequireHomeRole requireHomeRole) throws Throwable {
        UserContext.UserInfo userInfo = UserContext.get();
        if (userInfo == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户未登录");
        }

        String userId = userInfo.getUserId();
        String homeId = getHomeIdFromRequest(joinPoint, requireHomeRole.homeIdParam());

        if (!StringUtils.hasText(homeId)) {
            log.error("鉴权拦截失败: 无法从请求中获取 homeId. 参数名配置: {}", requireHomeRole.homeIdParam());
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "缺少必须的家庭ID");
        }

        // 查询缓存获取角色
        Integer role = homeCacheManager.getUserRole(homeId, userId);

        if (role == null) {
            log.warn("鉴权拦截: 用户 {} 未加入家庭 {}", userId, homeId);
            throw new BusinessException(ResultCode.FORBIDDEN, "未加入该家庭");
        }

        // 角色校验：数字越小权限越大 (1:Owner, 2:Admin, 3:Member)
        if (role > requireHomeRole.minRole()) {
            log.warn("鉴权拦截: 用户 {} 在家庭 {} 中的角色为 {}, 要求的最小角色为 {}", userId, homeId, role, requireHomeRole.minRole());
            throw new BusinessException(ResultCode.FORBIDDEN, "权限不足");
        }
    }

    /**
     * 尝试从请求路径、参数或方法体中提取 homeId
     */
    private String getHomeIdFromRequest(JoinPoint joinPoint, String paramName) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();

        // 1. 先尝试从 URL Path Variables 中获取 (例如 /api/v1/homes/{homeId})
        Map<String, String> pathVariables = (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (pathVariables != null && pathVariables.containsKey(paramName)) {
            return pathVariables.get(paramName);
        }

        // 2. 尝试从 Request Parameter 获取 (例如 ?homeId=xxx)
        String paramValue = request.getParameter(paramName);
        if (StringUtils.hasText(paramValue)) {
            return paramValue;
        }

        // 3. 尝试从 Request Body (JSON 反序列化后的 DTO) 中获取
        Object[] args = joinPoint.getArgs();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] parameterNames = signature.getParameterNames();

        for (int i = 0; i < parameterNames.length; i++) {
            Object arg = args[i];
            if (arg != null) {
                try {
                    // 反射尝试调用 getHomeId()
                    Method getHomeIdMethod = arg.getClass().getMethod("get" + StringUtils.capitalize(paramName));
                    Object result = getHomeIdMethod.invoke(arg);
                    if (result instanceof String) {
                        return (String) result;
                    }
                } catch (Exception ignored) {
                    // 忽略，说明该 DTO 没有对应的 getter
                }
            }
        }

        return null;
    }
}
