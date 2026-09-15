package com.aiot.device.service;

import com.aiot.common.dto.ai.AiBusinessLiveFlowSnapshot;
import com.aiot.common.dto.ai.AiPersistenceAdminQueryResp;
import com.aiot.common.dto.ai.AiPersistenceHistoryDetailResp;

public interface AiPersistenceAdminFacade {
    AiPersistenceAdminQueryResp query();

    AiBusinessLiveFlowSnapshot getBusinessLiveFlow();

    AiPersistenceHistoryDetailResp getHistoryDetail(String reportType, Long occurredAt);
}
