package com.aiot.rule.service;

import com.aiot.rule.model.AiMysqlWriteOutboxTask;
import com.aiot.rule.repository.AiMysqlRecordWriter;
import com.aiot.rule.repository.AiMysqlWriteOutboxRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiMysqlWriteOutboxReplaySchedulerTest {

    @Test
    void shouldReplayPendingOutboxTasksWhenEnabled() {
        AiMysqlWriteOutboxRepository repository = mock(AiMysqlWriteOutboxRepository.class);
        AiMysqlRecordWriter writer = mock(AiMysqlRecordWriter.class);
        AiMysqlWriteOutboxTask task = AiMysqlWriteOutboxTask.builder()
                .taskId("task-1")
                .entityType("diagnosis")
                .recordKey("diag-1")
                .payloadJson("{}")
                .build();
        when(repository.listPending(16)).thenReturn(List.of(task));

        AiMysqlWriteOutboxReplayScheduler scheduler = new AiMysqlWriteOutboxReplayScheduler(repository, writer, true, 16);

        scheduler.replayPendingWrites();

        verify(repository).listPending(16);
        verify(writer).replay(task);
    }
}
