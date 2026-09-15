package com.aiot.rule.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class AiControlPlaneDrainRequest {
    @NotBlank(message = "operator 不能为空")
    private String operator;
    private Boolean dryRun;
    private Integer batchSize;
    private List<String> stores;
}
