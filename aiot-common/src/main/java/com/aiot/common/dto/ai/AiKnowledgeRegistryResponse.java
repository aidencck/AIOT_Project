package com.aiot.common.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiKnowledgeRegistryResponse {
    private String source;
    private String sourceVersion;
    private Integer importedFileCount;
    private Integer importedChunkCount;
    private List<AiKnowledgeSourceItem> items;
}
