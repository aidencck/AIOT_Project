package com.aiot.device.aspect;

import com.aiot.common.api.ResultCode;
import com.aiot.common.exception.BusinessException;
import com.aiot.device.annotation.RequireHomePermission;
import com.aiot.device.annotation.ResourceType;
import com.aiot.device.entity.Device;
import com.aiot.device.entity.OtaUpgradeTask;
import com.aiot.device.repository.DeviceRepository;
import com.aiot.device.repository.OtaUpgradeTaskRepository;
import com.aiot.device.security.HomePermissionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerMapping;

import java.lang.reflect.Method;
import java.util.Map;

@Slf4j
@Aspect
@Component
@Order(20)
public class RequireHomePermissionAspect {

    private final HomePermissionService homePermissionService;
    private final DeviceRepository deviceRepository;
    private final OtaUpgradeTaskRepository otaUpgradeTaskRepository;

    public RequireHomePermissionAspect(HomePermissionService homePermissionService,
                                       DeviceRepository deviceRepository,
                                       OtaUpgradeTaskRepository otaUpgradeTaskRepository) {
        this.homePermissionService = homePermissionService;
        this.deviceRepository = deviceRepository;
        this.otaUpgradeTaskRepository = otaUpgradeTaskRepository;
    }

    @Before("@annotation(requireHomePermission)")
    public void before(JoinPoint joinPoint, RequireHomePermission requireHomePermission) {
        String homeId = extractHomeId(joinPoint, requireHomePermission.homeIdParam());
        if (!StringUtils.hasText(homeId)) {
            homeId = resolveHomeIdByResource(joinPoint, requireHomePermission);
        }
        if (!StringUtils.hasText(homeId)) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "缺少家庭标识，无法鉴权");
        }
        homePermissionService.requireHomePermission(homeId, requireHomePermission.minRole(), requireHomePermission.denyMessage());
    }

    private String resolveHomeIdByResource(JoinPoint joinPoint, RequireHomePermission requireHomePermission) {
        if (requireHomePermission.resourceType() == ResourceType.NONE) {
            return null;
        }
        String resourceParam = requireHomePermission.resourceIdParam();
        if (!StringUtils.hasText(resourceParam)) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "未配置资源参数名");
        }
        String resourceId = extractHomeId(joinPoint, resourceParam);
        if (!StringUtils.hasText(resourceId)) {
            return null;
        }
        if (requireHomePermission.resourceType() == ResourceType.DEVICE) {
            Device device = deviceRepository.selectByIdentity(resourceId);
            if (device == null) {
                throw new BusinessException(ResultCode.DEVICE_NOT_FOUND, "设备不存在");
            }
            return device.getHomeId();
        }
        if (requireHomePermission.resourceType() == ResourceType.OTA_TASK) {
            OtaUpgradeTask task = otaUpgradeTaskRepository.selectByTaskId(resourceId);
            if (task == null) {
                throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "OTA任务不存在");
            }
            return task.getHomeId();
        }
        return null;
    }

    private String extractHomeId(JoinPoint joinPoint, String paramName) {
        if (!StringUtils.hasText(paramName)) {
            return null;
        }
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            Object uriVars = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
            if (uriVars instanceof Map<?, ?> pathVariables) {
                Object pathValue = pathVariables.get(paramName);
                if (pathValue instanceof String pathStr && StringUtils.hasText(pathStr)) {
                    return pathStr;
                }
            }
            String requestParam = request.getParameter(paramName);
            if (StringUtils.hasText(requestParam)) {
                return requestParam;
            }
        } else {
            log.debug("RequestAttributes 缺失，跳过 request 层参数提取");
        }

        Object[] args = joinPoint.getArgs();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] parameterNames = signature.getParameterNames();
        for (int i = 0; i < parameterNames.length; i++) {
            if (StringUtils.hasText(parameterNames[i]) && parameterNames[i].equals(paramName) && args[i] instanceof String value) {
                return value;
            }
            Object arg = args[i];
            if (arg == null) {
                continue;
            }
            try {
                Method getter = arg.getClass().getMethod("get" + capitalizeFirst(paramName));
                Object value = getter.invoke(arg);
                if (value instanceof String str && StringUtils.hasText(str)) {
                    return str;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private String capitalizeFirst(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        if (value.length() == 1) {
            return value.toUpperCase();
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
