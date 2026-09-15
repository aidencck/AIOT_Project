package com.aiot.gateway.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GatewaySecurityConfigTest {

    @Test
    void shouldExcludeOpenApiPathsFromWhitelistWhenPublicOpenApiDisabled() {
        assertThat(GatewaySecurityConfig.buildWhitelist(false))
                .contains("/api/v1/users/login", "/api/v1/users/register")
                .doesNotContain("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html");
    }

    @Test
    void shouldIncludeOpenApiPathsInWhitelistWhenPublicOpenApiEnabled() {
        assertThat(GatewaySecurityConfig.buildWhitelist(true))
                .contains("/api/v1/users/login", "/api/v1/users/register")
                .contains("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html");
    }
}
