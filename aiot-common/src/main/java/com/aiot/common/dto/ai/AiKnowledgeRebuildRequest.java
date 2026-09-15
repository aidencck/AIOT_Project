package com.aiot.common.dto.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AiKnowledgeRebuildRequest {
    @NotBlank(message = "productKey 不能为空")
    @Size(max = 64, message = "productKey 长度不能超过64")
    private String productKey;

    @NotBlank(message = "thingModelJson 不能为空")
    private String thingModelJson;

    @Size(max = 64, message = "source 长度不能超过64")
    private String source;
}
