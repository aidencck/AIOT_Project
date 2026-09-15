package com.aiot.common.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiKnowledgeImportResponse {
    private Integer importedFileCount;
    private Integer importedChunkCount;
    private String source;
    private String sourceVersion;
}
