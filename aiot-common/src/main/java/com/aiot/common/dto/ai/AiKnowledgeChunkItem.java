package com.aiot.common.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiKnowledgeChunkItem {
    private String chunkId;
    private String productKey;
    private String chunkType;
    private String title;
    private String content;
    private Double score;
}
