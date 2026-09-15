package com.aiot.rule.model;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiCaseRecord {
    private String caseId;
    private String sceneType;
    private String symptom;
    private String rootCause;
    private String resolution;
    private Double effectivenessScore;
    private String sourceFeedbackId;
    private Long createdAt;
}
