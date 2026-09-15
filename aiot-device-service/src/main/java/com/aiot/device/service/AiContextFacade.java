package com.aiot.device.service;

import com.aiot.common.dto.ai.AiDeviceContextResp;
import com.aiot.common.dto.ai.AiRuntimeContextPayload;

public interface AiContextFacade {
    AiDeviceContextResp buildDeviceContext(String deviceId, String sceneType);

    AiRuntimeContextPayload buildRuntimeContext(String deviceId, String sceneType);
}
