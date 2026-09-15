package com.aiot.rule.service;

import com.aiot.rule.config.AiPersistenceReadMode;

public record AiPersistenceReadDecision(
        AiPersistenceReadMode configuredMode,
        AiPersistenceReadMode effectiveMode,
        boolean mysqlCutoverReady,
        String blockReason
) {
}
