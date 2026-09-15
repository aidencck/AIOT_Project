package com.aiot.rule.service;

import com.aiot.rule.model.AiCaseMaterializationTask;
import com.aiot.rule.repository.AiCaseMaterializationTaskRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AiCaseMaterializationReplayScheduler {

    private final AiCaseMaterializationTaskRepository aiCaseMaterializationTaskRepository;
    private final AiDiagnosisService aiDiagnosisService;
    private final boolean replayEnabled;
    private final int batchSize;

    public AiCaseMaterializationReplayScheduler(
            AiCaseMaterializationTaskRepository aiCaseMaterializationTaskRepository,
            AiDiagnosisService aiDiagnosisService,
            @Value("${aiot.ai.case-materialization.replay-enabled:true}") boolean replayEnabled,
            @Value("${aiot.ai.case-materialization.batch-size:16}") int batchSize) {
        this.aiCaseMaterializationTaskRepository = aiCaseMaterializationTaskRepository;
        this.aiDiagnosisService = aiDiagnosisService;
        this.replayEnabled = replayEnabled;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${aiot.ai.case-materialization.fixed-delay-ms:30000}")
    public void replayPendingCases() {
        if (!replayEnabled) {
            return;
        }
        List<AiCaseMaterializationTask> tasks = aiCaseMaterializationTaskRepository.listPending(batchSize);
        for (AiCaseMaterializationTask task : tasks) {
            aiDiagnosisService.replayCaseMaterialization(task);
        }
    }
}
