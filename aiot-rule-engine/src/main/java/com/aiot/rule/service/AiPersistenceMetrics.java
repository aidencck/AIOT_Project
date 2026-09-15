package com.aiot.rule.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class AiPersistenceMetrics {

    private final MeterRegistry meterRegistry;

    public AiPersistenceMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordRead(String repository, String mode, String source, boolean hit) {
        meterRegistry.counter(
                "aiot.ai.persistence.read.total",
                "repository", repository,
                "mode", mode,
                "source", source,
                "result", hit ? "hit" : "miss"
        ).increment();
    }

    public void recordFallback(String repository, String from, String to, boolean hit) {
        meterRegistry.counter(
                "aiot.ai.persistence.read.fallback.total",
                "repository", repository,
                "from", from,
                "to", to,
                "result", hit ? "hit" : "miss"
        ).increment();
    }

    public void recordMysqlWrite(String table, String result, long durationNanos) {
        meterRegistry.counter(
                "aiot.ai.persistence.mysql.write.total",
                "table", table,
                "result", result
        ).increment();
        Timer.builder("aiot.ai.persistence.mysql.write.latency")
                .tag("table", table)
                .register(meterRegistry)
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }

    public void recordMysqlRead(String entity, String result, long durationNanos) {
        meterRegistry.counter(
                "aiot.ai.persistence.mysql.read.total",
                "entity", entity,
                "result", result
        ).increment();
        Timer.builder("aiot.ai.persistence.mysql.read.latency")
                .tag("entity", entity)
                .register(meterRegistry)
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }

    public void recordMysqlOutbox(String entity, String action) {
        Counter.builder("aiot.ai.persistence.mysql.outbox.total")
                .tag("entity", entity)
                .tag("action", action)
                .register(meterRegistry)
                .increment();
    }

    public void recordCaseMaterialization(String action) {
        Counter.builder("aiot.ai.case.materialization.total")
                .tag("action", action)
                .register(meterRegistry)
                .increment();
    }

    public void recordLlmRefine(String action) {
        Counter.builder("aiot.ai.llm.refine.total")
                .tag("action", action)
                .register(meterRegistry)
                .increment();
    }
}
