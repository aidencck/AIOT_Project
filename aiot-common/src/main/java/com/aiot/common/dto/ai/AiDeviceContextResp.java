package com.aiot.common.dto.ai;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiDeviceContextResp {
    private String sceneType;
    private String deviceId;
    private String globalDeviceId;
    private String authIdentity;
    private String deviceSn;
    private String deviceName;
    private String productKey;
    private String homeId;
    private String roomId;
    private String gatewayId;
    private Integer status;
    private String firmwareVersion;
    private String lastHeartbeatTime;
    private String onlineStatus;
    private String thingModelJson;
    private String deviceModelJson;
    private Map<String, Object> shadowSummary;
    private List<Map<String, Object>> recentEvents;
    private Map<String, Object> opsSummary;
}
