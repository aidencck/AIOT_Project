package com.aiot.rule.client;

import com.aiot.common.dto.ai.AiRuntimeContextPayload;

public interface AiContextProvider {
    AiRuntimeContextPayload getRuntimeContext(String deviceId, String sceneType);
}
