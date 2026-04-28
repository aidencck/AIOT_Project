package com.aiot.device.service;

import java.util.Map;

public interface DeviceShadowService {
    /**
     * 更新设备影子（上报属性）
     */
    void updateReportedShadow(String deviceId, Map<String, Object> reported, Long expectedVersion);

    /**
     * 下发期望属性（设置期望值）
     */
    void updateDesiredShadow(String deviceId, Map<String, Object> desired, Long expectedVersion);

    /**
     * 获取完整的设备影子
     */
    Map<String, Object> getDeviceShadow(String deviceId);
}
