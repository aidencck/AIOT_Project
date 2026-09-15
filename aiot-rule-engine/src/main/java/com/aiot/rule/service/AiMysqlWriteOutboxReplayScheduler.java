package com.aiot.rule.service;

import com.aiot.rule.model.AiMysqlWriteOutboxTask;
import com.aiot.rule.repository.AiMysqlRecordWriter;
import com.aiot.rule.repository.AiMysqlWriteOutboxRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AiMysqlWriteOutboxReplayScheduler {

    private final AiMysqlWriteOutboxRepository aiMysqlWriteOutboxRepository;
    private final AiMysqlRecordWriter aiMysqlRecordWriter;
    private final boolean replayEnabled;
    private final int batchSize;

    public AiMysqlWriteOutboxReplayScheduler(
            AiMysqlWriteOutboxRepository aiMysqlWriteOutboxRepository,
            AiMysqlRecordWriter aiMysqlRecordWriter,
            @Value("${aiot.ai.persistence.mysql.outbox.replay-enabled:true}") boolean replayEnabled,
            @Value("${aiot.ai.persistence.mysql.outbox.batch-size:32}") int batchSize) {
        this.aiMysqlWriteOutboxRepository = aiMysqlWriteOutboxRepository;
        this.aiMysqlRecordWriter = aiMysqlRecordWriter;
        this.replayEnabled = replayEnabled;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${aiot.ai.persistence.mysql.outbox.fixed-delay-ms:30000}")
    public void replayPendingWrites() {
        if (!replayEnabled) {
            return;
        }
        List<AiMysqlWriteOutboxTask> tasks = aiMysqlWriteOutboxRepository.listPending(batchSize);
        for (AiMysqlWriteOutboxTask task : tasks) {
            aiMysqlRecordWriter.replay(task);
        }
    }
}
