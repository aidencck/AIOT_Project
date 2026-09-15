package com.aiot.rule.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class AiCaseMaterializationTask {
    private String taskId;
    private String diagnosisId;
    private String feedbackId;
    private String feedbackType;
    private String resolutionStatus;
    private String resolutionNote;
    private String operatorId;
    private Long queuedAt;
    private Long lastRetryAt;
    private Integer retryCount;
    private String lastError;
}
