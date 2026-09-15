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
public class AiKnowledgeSearchResponse {
    private String productKey;
    private String sceneType;
    private List<AiKnowledgeChunkItem> chunks;
}
