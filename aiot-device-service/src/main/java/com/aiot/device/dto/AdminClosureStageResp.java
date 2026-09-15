package com.aiot.device.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminClosureStageResp {
    private String stageKey;
    private String stageName;
    private String status;
    private String summary;
    private String actionHint;
}
