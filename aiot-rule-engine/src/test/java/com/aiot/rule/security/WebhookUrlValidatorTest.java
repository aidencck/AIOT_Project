package com.aiot.rule.security;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebhookUrlValidatorTest {

    private final WebhookUrlValidator validator = new WebhookUrlValidator();

    @Test
    void shouldAllowPublicHttpsUrl() {
        assertThatCode(() -> validator.validate("https://example.com/hook"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectLoopbackUrl() {
        assertThatThrownBy(() -> validator.validate("http://127.0.0.1/hook"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectCloudMetadataUrl() {
        assertThatThrownBy(() -> validator.validate("http://169.254.169.254/latest/meta-data/"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectPrivateNetworkUrl() {
        assertThatThrownBy(() -> validator.validate("http://10.0.0.1/hook"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectNonHttpScheme() {
        assertThatThrownBy(() -> validator.validate("ftp://example.com/hook"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldAllowHostWhenMatchedInWhitelist() {
        ReflectionTestUtils.setField(validator, "allowedHosts", "10.0.0.1");
        assertThatCode(() -> validator.validate("http://10.0.0.1/hook"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectUnresolvableHost() {
        assertThatThrownBy(() -> validator.validate("http://nonexistent-host.invalid/hook"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
