package com.aiot.common.dto.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AiFeedbackRequest {
    @NotBlank(message = "diagnosisId 不能为空")
    @Size(max = 64, message = "diagnosisId 长度不能超过64")
    private String diagnosisId;

    @NotBlank(message = "feedbackType 不能为空")
    @Size(max = 32, message = "feedbackType 长度不能超过32")
    private String feedbackType;

    @NotBlank(message = "resolutionStatus 不能为空")
    @Size(max = 32, message = "resolutionStatus 长度不能超过32")
    private String resolutionStatus;

    @Size(max = 64, message = "operatorId 长度不能超过64")
    private String operatorId;

    @Size(max = 1000, message = "resolutionNote 长度不能超过1000")
    private String resolutionNote;
}
