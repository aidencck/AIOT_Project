package com.aiot.common.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiKnowledgeSourceItem {
    private String filePath;
    private String sourceType;
    private String contentHash;
    private Long importedAt;
    private Integer chunkCount;
    private Boolean changed;
}
