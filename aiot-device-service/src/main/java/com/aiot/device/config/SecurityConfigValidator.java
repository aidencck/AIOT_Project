package com.aiot.device.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class SecurityConfigValidator {

    @Value("${aiot.internal.token}")
    private String internalToken;

    @Value("${aiot.security.jwt.secret}")
    private String jwtSecret;

    @Value("${emqx.api.password}")
    private String emqxApiPassword;

    @PostConstruct
    public void validate() {
        assertStrong("aiot.internal.token", internalToken, 24);
        assertStrong("aiot.security.jwt.secret", jwtSecret, 32);
        assertStrong("emqx.api.password", emqxApiPassword, 12);
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
