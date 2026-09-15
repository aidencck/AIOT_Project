package com.aiot.common.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiPersistenceAdminQueryResp {
    private AiPersistenceStatusSnapshot status;
    private AiControlPlaneDrainSnapshot controlPlaneDrain;
    private AiBusinessLiveFlowSnapshot businessLiveFlow;
}
