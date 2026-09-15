package com.aiot.common.dto.ai;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AiKnowledgeSearchRequest {
    @Size(max = 64, message = "productKey 长度不能超过64")
    private String productKey;

    private String thingModelJson;

    @Size(max = 64, message = "sceneType 长度不能超过64")
    private String sceneType;

    @Size(max = 256, message = "query 长度不能超过256")
    private String query;

    private Integer topK;
}
