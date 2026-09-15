package com.aiot.common.ai.audit;

public interface AiAuditPublisher {

    void publishDiagnosis(String traceId,
                          String sceneType,
                          String modelName,
                          String promptVersion,
                          boolean llmEnabled,
                          boolean usedFallback,
                          long latencyMs);
}
