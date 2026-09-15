package com.aiot.device.aspect;

import com.aiot.common.api.ResultCode;
import com.aiot.common.exception.BusinessException;
import com.aiot.device.annotation.RequireHomePermission;
import com.aiot.device.dto.DeviceReq;
import com.aiot.device.entity.Device;
import com.aiot.device.entity.OtaUpgradeTask;
import com.aiot.device.repository.DeviceRepository;
import com.aiot.device.repository.OtaUpgradeTaskRepository;
import com.aiot.device.security.HomePermissionService;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerMapping;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RequireHomePermissionAspectTest {

    private HomePermissionService homePermissionService;
    private DeviceRepository deviceRepository;
    private OtaUpgradeTaskRepository otaUpgradeTaskRepository;
    private RequireHomePermissionAspect aspect;

    @BeforeEach
    void setUp() {
        homePermissionService = mock(HomePermissionService.class);
        deviceRepository = mock(DeviceRepository.class);
        otaUpgradeTaskRepository = mock(OtaUpgradeTaskRepository.class);
        aspect = new RequireHomePermissionAspect(homePermissionService, deviceRepository, otaUpgradeTaskRepository);
    }

    @AfterEach
    void cleanRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void shouldAuthorizeByHomeIdFromBody() throws Exception {
        DeviceReq req = new DeviceReq();
        req.setHomeId("home-1");

        JoinPoint joinPoint = mockJoinPoint("createDevice", new Object[]{req}, new String[]{"req"}, DeviceReq.class);
        RequireHomePermission annotation = annotation("createDevice", DeviceReq.class);

        aspect.before(joinPoint, annotation);

        verify(homePermissionService).requireHomePermission("home-1", 2, "无权限在该家庭创建设备");
    }

    @Test
    void shouldAuthorizeByDeviceResourceId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("deviceId", "dev-1"));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        Device device = new Device();
        device.setHomeId("home-2");
        when(deviceRepository.selectByIdentity("dev-1")).thenReturn(device);

        JoinPoint joinPoint = mockJoinPoint("getDevice", new Object[]{"dev-1"}, new String[]{"deviceId"}, String.class);
        RequireHomePermission annotation = annotation("getDevice", String.class);

        aspect.before(joinPoint, annotation);

        verify(homePermissionService).requireHomePermission("home-2", 3, "无权限访问该设备");
    }

    @Test
    void shouldAuthorizeByOtaTaskResourceId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("taskId", "task-1"));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        OtaUpgradeTask task = new OtaUpgradeTask();
        task.setHomeId("home-3");
        when(otaUpgradeTaskRepository.selectByTaskId("task-1")).thenReturn(task);

        JoinPoint joinPoint = mockJoinPoint("getOtaTask", new Object[]{"task-1"}, new String[]{"taskId"}, String.class);
        RequireHomePermission annotation = annotation("getOtaTask", String.class);

        aspect.before(joinPoint, annotation);

        verify(homePermissionService).requireHomePermission("home-3", 3, "无权限访问该OTA任务");
    }

    @Test
    void shouldReturnDeviceNotFoundWhenDeviceResourceMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("deviceId", "dev-missing"));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        JoinPoint joinPoint = mockJoinPoint("getDevice", new Object[]{"dev-missing"}, new String[]{"deviceId"}, String.class);
        RequireHomePermission annotation = annotation("getDevice", String.class);

        BusinessException ex = assertThrows(BusinessException.class, () -> aspect.before(joinPoint, annotation));
        assertEquals(ResultCode.DEVICE_NOT_FOUND, ex.getResultCode());
        assertEquals("设备不存在", ex.getMessage());
    }

    @Test
    void shouldReturnResourceNotFoundWhenOtaTaskMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("taskId", "task-missing"));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        JoinPoint joinPoint = mockJoinPoint("getOtaTask", new Object[]{"task-missing"}, new String[]{"taskId"}, String.class);
        RequireHomePermission annotation = annotation("getOtaTask", String.class);

        BusinessException ex = assertThrows(BusinessException.class, () -> aspect.before(joinPoint, annotation));
        assertEquals(ResultCode.RESOURCE_NOT_FOUND, ex.getResultCode());
        assertEquals("OTA任务不存在", ex.getMessage());
    }

    @Test
    void shouldFailWhenHomeIdCannotBeResolved() throws Exception {
        JoinPoint joinPoint = mockJoinPoint("getDevice", new Object[]{""}, new String[]{"deviceId"}, String.class);
        RequireHomePermission annotation = annotation("getDevice", String.class);

        BusinessException ex = assertThrows(BusinessException.class, () -> aspect.before(joinPoint, annotation));
        assertEquals(ResultCode.VALIDATE_FAILED, ex.getResultCode());
        assertEquals("缺少家庭标识，无法鉴权", ex.getMessage());
    }

    private RequireHomePermission annotation(String methodName, Class<?>... paramTypes) throws Exception {
        Method method = FakeController.class.getDeclaredMethod(methodName, paramTypes);
        return method.getAnnotation(RequireHomePermission.class);
    }

    private JoinPoint mockJoinPoint(String methodName, Object[] args, String[] parameterNames, Class<?>... paramTypes) throws Exception {
        JoinPoint joinPoint = mock(JoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        Method method = FakeController.class.getDeclaredMethod(methodName, paramTypes);
        when(joinPoint.getArgs()).thenReturn(args);
        when(joinPoint.getSignature()).thenReturn((Signature) signature);
        when(signature.getMethod()).thenReturn(method);
        when(signature.getParameterNames()).thenReturn(parameterNames);
        return joinPoint;
    }

    static class FakeController {
        @RequireHomePermission(minRole = 2, denyMessage = "无权限在该家庭创建设备")
        public void createDevice(DeviceReq req) {
        }

        @RequireHomePermission(minRole = 3, denyMessage = "无权限访问该设备", resourceType = com.aiot.device.annotation.ResourceType.DEVICE, resourceIdParam = "deviceId")
        public void getDevice(String deviceId) {
        }

        @RequireHomePermission(minRole = 3, denyMessage = "无权限访问该OTA任务", resourceType = com.aiot.device.annotation.ResourceType.OTA_TASK, resourceIdParam = "taskId")
        public void getOtaTask(String taskId) {
        }
    }
}
