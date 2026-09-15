package com.aiot.common.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "ai.llm")
public class AiLlmProperties {
    private boolean enabled = false;
    private String baseUrl;
    private String apiKey;
    private String provider = "openai-compatible";
    private String model = "gpt-4o-mini";
    private long timeoutMs = 3000L;
    private double temperature = 0.2D;
    private Integer numPredict;
    private Integer numCtx;
}
