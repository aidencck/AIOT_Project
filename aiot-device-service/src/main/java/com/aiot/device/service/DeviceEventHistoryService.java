package com.aiot.device.service;

import java.util.List;
import java.util.Map;

public interface DeviceEventHistoryService {

    /**
     * 记录一条设备事件历史到 per-device Redis List。
     *
     * @param deviceId    设备 ID（globalDeviceId）
     * @param eventType   事件类型
     * @param status      设备状态值
     * @param epochMillis 事件发生时间戳（毫秒）
     */
    void record(String deviceId, String eventType, Integer status, long epochMillis);

    /**
     * 读取设备最近的事件历史。
     *
     * @param deviceId 设备 ID（globalDeviceId）
     * @param limit    最多返回条数
     * @return 事件历史列表，空或异常时返回空列表
     */
    List<Map<String, Object>> recentEvents(String deviceId, int limit);
}
