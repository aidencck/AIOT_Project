package com.aiot.rule.service;

import com.aiot.common.ai.audit.AiAuditPublisher;
import com.aiot.rule.model.AuditRecord;
import com.aiot.rule.repository.OpsRecordRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
public class AiAuditPublisherImpl implements AiAuditPublisher {

    private final OpsRecordRepository opsRecordRepository;

    public AiAuditPublisherImpl(OpsRecordRepository opsRecordRepository) {
        this.opsRecordRepository = opsRecordRepository;
    }

    @Override
    public void publishDiagnosis(String traceId, String sceneType, String modelName, String promptVersion,
                                 boolean llmEnabled, boolean usedFallback, long latencyMs) {
        log.info("AI diagnosis audit: traceId={}, sceneType={}, modelName={}, promptVersion={}, llmEnabled={}, usedFallback={}, latencyMs={}",
                traceId, sceneType, modelName, promptVersion, llmEnabled, usedFallback, latencyMs);
        try {
            AuditRecord audit = AuditRecord.builder()
                    .auditId(UUID.randomUUID().toString())
                    .eventType("AI_DIAGNOSIS")
                    .operator("system")
                    .targetId(traceId)
                    .details("modelName=" + modelName + ", llmEnabled=" + llmEnabled + ", usedFallback=" + usedFallback + ", latencyMs=" + latencyMs)
                    .traceId(traceId)
                    .createdAt(System.currentTimeMillis())
                    .build();
            opsRecordRepository.saveAudit(audit);
        } catch (Exception e) {
            log.warn("AI 诊断审计落库失败, traceId={}", traceId, e);
        }
    }
}
