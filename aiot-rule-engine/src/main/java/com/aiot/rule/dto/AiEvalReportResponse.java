package com.aiot.rule.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiEvalReportResponse {
    private String sceneType;
    private String reportType;
    private String reportPath;
    private Boolean exists;
    private Boolean gatePassed;
    private Long generatedAt;
    private Map<String, Object> content;
}
