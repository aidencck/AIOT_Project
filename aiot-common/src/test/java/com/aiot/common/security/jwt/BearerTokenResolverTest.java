package com.aiot.common.security.jwt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BearerTokenResolverTest {

    @Test
    void shouldReturnNullForBlankAuthorizationHeader() {
        assertNull(BearerTokenResolver.resolveFromAuthorizationHeader(" "));
    }

    @Test
    void shouldReturnNullForNonBearerHeader() {
        assertNull(BearerTokenResolver.resolveFromAuthorizationHeader("Basic abc"));
    }

    @Test
    void shouldReturnNullWhenBearerTokenIsBlank() {
        assertNull(BearerTokenResolver.resolveFromAuthorizationHeader("Bearer "));
    }

    @Test
    void shouldResolveBearerToken() {
        assertEquals("token-123", BearerTokenResolver.resolveFromAuthorizationHeader("Bearer token-123"));
    }
}
