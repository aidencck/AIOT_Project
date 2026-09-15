package com.aiot.rule.model;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiDiagnosisRecord {
    private String diagnosisId;
    private String traceId;
    private String sceneType;
    private String deviceId;
    private String homeId;
    private String eventId;
    private String contextSnapshot;
    private String modelName;
    private String promptVersion;
    private String diagnosisResult;
    private Long latencyMs;
    private Long createdAt;
}
