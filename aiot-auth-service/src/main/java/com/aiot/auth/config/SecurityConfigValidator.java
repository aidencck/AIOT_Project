package com.aiot.auth.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class SecurityConfigValidator {

    @Value("${aiot.internal.token}")
    private String internalToken;

    @Value("${aiot.emqx.webhook.secret}")
    private String webhookSecret;

    @PostConstruct
    public void validate() {
        assertStrong("aiot.internal.token", internalToken, 24);
        assertStrong("aiot.emqx.webhook.secret", webhookSecret, 24);
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
