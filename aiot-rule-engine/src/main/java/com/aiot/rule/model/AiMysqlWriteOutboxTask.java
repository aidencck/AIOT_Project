package com.aiot.rule.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class AiMysqlWriteOutboxTask {
    private String taskId;
    private String entityType;
    private String recordKey;
    private String payloadJson;
    private String lastError;
    private Long failedAt;
    private Long lastRetryAt;
    private Integer retryCount;
    private Boolean deadLetter;
}
