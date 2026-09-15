package com.aiot.common.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiFeedbackResponse {
    private Boolean accepted;
    private String diagnosisId;
    private String feedbackId;
    private Boolean feedbackSaved;
    private Boolean caseRequested;
    private Boolean caseSaved;
    private String caseStatus;
    private String caseId;
    private String caseTaskId;
    private String message;
}
