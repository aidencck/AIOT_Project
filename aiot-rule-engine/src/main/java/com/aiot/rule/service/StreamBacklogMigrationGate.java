package com.aiot.rule.service;

import com.aiot.rule.listener.DeviceEventPendingRecoveryScheduler;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.PendingMessagesSummary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 事件中枢积压迁移硬门禁。
 *
 * 当消费组 PEL（未确认消息数，真正的积压口径）超过阈值时，自动触发一次
 * pending 回收（XCLAIM 迁移回处理链路），避免积压无限增长导致 AI 诊断时效性失效。
 *
 * 与 {@link StreamBacklogMetrics} 职责分离：后者只负责观测（gauge），本组件负责
 * 控制动作（迁移触发），满足“职责收缩 / 可剥离”边界。
 */
@Slf4j
@Component
public class StreamBacklogMigrationGate {

    private final StringRedisTemplate stringRedisTemplate;
    private final DeviceEventPendingRecoveryScheduler recoveryScheduler;
    private final String streamKey;
    private final String group;
    private final boolean enabled;
    private final long overflowThreshold;
    private final Counter migrationTriggeredCounter;

    public StreamBacklogMigrationGate(
            StringRedisTemplate stringRedisTemplate,
            DeviceEventPendingRecoveryScheduler recoveryScheduler,
            MeterRegistry meterRegistry,
            @Value("${aiot.events.device-status-stream:aiot:stream:device-event}") String streamKey,
            @Value("${aiot.events.device-status-stream-group:aiot-rule-engine-group}") String group,
            @Value("${aiot.events.backlog-migration.enabled:true}") boolean enabled,
            @Value("${aiot.events.backlog-migration.threshold:10000}") long overflowThreshold) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.recoveryScheduler = recoveryScheduler;
        this.streamKey = streamKey;
        this.group = group;
        this.enabled = enabled;
        this.overflowThreshold = overflowThreshold;
        this.migrationTriggeredCounter = Counter.builder("aiot.stream.backlog.migration.triggered.total")
                .tag("service", "aiot-rule-engine")
                .tag("stream", streamKey)
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${aiot.events.backlog-migration.fixed-delay-ms:30000}")
    public void checkAndMigrate() {
        if (!enabled || overflowThreshold <= 0) {
            return;
        }
        try {
            long backlog = currentBacklog();
            if (backlog < overflowThreshold) {
                return;
            }
            migrationTriggeredCounter.increment();
            log.warn("事件中枢积压超阈值，触发迁移: stream={}, group={}, backlog={}, threshold={}",
                    streamKey, group, backlog, overflowThreshold);
            recoveryScheduler.recoverPending();
        } catch (Exception ex) {
            log.warn("Failed to run backlog migration, stream={}, group={}", streamKey, group, ex);
        }
    }

    private long currentBacklog() {
        PendingMessagesSummary summary = stringRedisTemplate.opsForStream().pending(streamKey, group);
        return summary == null ? 0L : summary.getTotalPendingMessages();
    }
}
