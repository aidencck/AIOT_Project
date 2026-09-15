package com.aiot.common.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiKnowledgeRebuildResponse {
    private String productKey;
    private Integer chunkCount;
    private Boolean rebuilt;
}
