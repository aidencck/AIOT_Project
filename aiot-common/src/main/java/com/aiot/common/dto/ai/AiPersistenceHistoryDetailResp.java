package com.aiot.common.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiPersistenceHistoryDetailResp {
    private String reportType;
    private Long occurredAt;
    private String reportPath;
    private Boolean exists;
    private String operator;
    private String scene;
    private Boolean success;
    private Boolean accepted;
    private Boolean dryRun;
    private String message;
    private Map<String, Object> content;
}
