package com.aiot.rule.service;

import com.aiot.rule.model.AiCaseMaterializationTask;
import com.aiot.rule.repository.AiCaseMaterializationTaskRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiCaseMaterializationReplaySchedulerTest {

    @Test
    void shouldReplayPendingCaseTasksWhenEnabled() {
        AiCaseMaterializationTaskRepository repository = mock(AiCaseMaterializationTaskRepository.class);
        AiDiagnosisService aiDiagnosisService = mock(AiDiagnosisService.class);
        AiCaseMaterializationTask task = AiCaseMaterializationTask.builder()
                .taskId("task-1")
                .diagnosisId("diag-1")
                .feedbackId("fb-1")
                .build();
        when(repository.listPending(8)).thenReturn(List.of(task));

        AiCaseMaterializationReplayScheduler scheduler = new AiCaseMaterializationReplayScheduler(
                repository,
                aiDiagnosisService,
                true,
                8
        );

        scheduler.replayPendingCases();

        verify(repository).listPending(8);
        verify(aiDiagnosisService).replayCaseMaterialization(task);
    }
}
