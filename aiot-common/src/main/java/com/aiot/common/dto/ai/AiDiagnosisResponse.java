package com.aiot.common.dto.ai;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiDiagnosisResponse {
    private String diagnosisId;
    private String sceneType;
    private String summary;
    private String rootCauseCategory;
    private Double confidence;
    private List<String> evidence;
    private List<String> recommendedActions;
    private Boolean ruleDraftable;
    private String riskLevel;
    private String modelName;
    private String promptVersion;
    private String source;
}
