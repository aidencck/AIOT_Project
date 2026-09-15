package com.aiot.rule.model;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiFeedbackRecord {
    private String feedbackId;
    private String diagnosisId;
    private String feedbackType;
    private String resolutionStatus;
    private String operatorId;
    private String resolutionNote;
    private Long createdAt;
}
