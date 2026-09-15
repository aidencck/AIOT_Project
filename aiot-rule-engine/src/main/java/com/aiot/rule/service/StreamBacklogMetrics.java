package com.aiot.rule.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.PendingMessagesSummary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 事件流观测指标。
 *
 * 指标口径（关键）：
 * - {@code aiot_stream_backlog_length}：真正的积压深度 = 消费组 PEL（未确认消息数）。
 *   过去误用 XLEN（流内总条目数），Redis Stream 在无 XTRIM/XDEL 时 XLEN 单调增长，
 *   等于「历史累计写入总量」而非「待消费积压」，导致 backlog 告警恒误报。
 * - {@code aiot_stream_length}：流内总条目数（XLEN），用于容量/保留策略监控。
 * - {@code aiot_stream_pending_total}：PEL 总量（与 backlog_length 同源，保留别名兼容既有 dashboard/recording rules）。
 */
@Slf4j
@Component
public class StreamBacklogMetrics {

    private final StringRedisTemplate stringRedisTemplate;
    private final String streamKey;
    private final String group;
    private final AtomicLong streamLength;
    private final AtomicLong backlogLength;
    private final AtomicLong pendingTotal;

    public StreamBacklogMetrics(
            StringRedisTemplate stringRedisTemplate,
            MeterRegistry meterRegistry,
            @Value("${aiot.events.device-status-stream:aiot:stream:device-event}") String streamKey,
            @Value("${aiot.events.device-status-stream-group:aiot-rule-engine-group}") String group) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.streamKey = streamKey;
        this.group = group;
        this.streamLength = new AtomicLong(0);
        this.backlogLength = new AtomicLong(0);
        this.pendingTotal = new AtomicLong(0);
        meterRegistry.gauge("aiot.stream.length",
                Tags.of("stream", streamKey, "group", group), streamLength);
        meterRegistry.gauge("aiot.stream.backlog.length",
                Tags.of("stream", streamKey, "group", group), backlogLength);
        meterRegistry.gauge("aiot.stream.pending.total",
                Tags.of("stream", streamKey, "group", group), pendingTotal);
    }

    @Scheduled(fixedDelayString = "${aiot.events.backlog-metrics.fixed-delay-ms:30000}")
    public void sampleBacklog() {
        try {
            Long length = stringRedisTemplate.opsForStream().size(streamKey);
            if (length != null) {
                streamLength.set(length);
            }
            PendingMessagesSummary summary = stringRedisTemplate.opsForStream().pending(streamKey, group);
            if (summary != null) {
                long pending = summary.getTotalPendingMessages();
                // backlog = PEL（未确认消息数），而非 XLEN（流内总条目数）
                backlogLength.set(pending);
                pendingTotal.set(pending);
            }
        } catch (Exception ex) {
            log.warn("Failed to sample stream backlog metrics, stream={}, group={}", streamKey, group, ex);
        }
    }
}
