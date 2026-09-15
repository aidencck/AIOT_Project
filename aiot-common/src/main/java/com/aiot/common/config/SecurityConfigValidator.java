package com.aiot.common.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class SecurityConfigValidator {

    private final Environment environment;

    @Value("${aiot.security.validate.entries:}")
    private String entries;

    public SecurityConfigValidator(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void validate() {
        if (!StringUtils.hasText(entries)) {
            return;
        }
        for (String entry : entries.split(",")) {
            String[] parts = entry.split(":");
            String key = parts[0].trim();
            int minLength = Integer.parseInt(parts[1].trim());
            String value = environment.getProperty(key);
            assertStrong(key, value, minLength);
        }
    }

    private void assertStrong(String key, String value, int minLength) {
        if (!StringUtils.hasText(value) || value.length() < minLength) {
            throw new IllegalStateException(key + " must be configured with a strong value, minLength=" + minLength);
        }
        String lower = value.toLowerCase();
        if (lower.contains("change-me") || lower.contains("public") || lower.contains("default")) {
            throw new IllegalStateException(key + " contains weak pattern and is not allowed");
        }
    }
}
