package com.aiot.rule.config;

public enum AiPersistenceReadMode {
    REDIS,
    MYSQL,
    DUAL;

    public static AiPersistenceReadMode from(String value) {
        if (value == null || value.isBlank()) {
            return REDIS;
        }
        try {
            return AiPersistenceReadMode.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return REDIS;
        }
    }
}
