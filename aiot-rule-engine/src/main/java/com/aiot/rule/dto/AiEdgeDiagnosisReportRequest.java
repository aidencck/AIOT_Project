package com.aiot.rule.dto;

import com.aiot.common.dto.ai.AiDiagnosisResponse;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiEdgeDiagnosisReportRequest {
    @NotBlank(message = "deviceId 不能为空")
    private String deviceId;
    private String sceneType;
    private String eventId;
    private AiDiagnosisResponse diagnosis;
}
