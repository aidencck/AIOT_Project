package com.aiot.common.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InternalTokenUtilsTest {

    @Test
    void shouldReturnFalseWhenAnyTokenIsNull() {
        assertFalse(InternalTokenUtils.matches(null, "provided"));
        assertFalse(InternalTokenUtils.matches("configured", null));
    }

    @Test
    void shouldReturnTrueWhenTokensMatch() {
        assertTrue(InternalTokenUtils.matches("same-token", "same-token"));
    }

    @Test
    void shouldReturnFalseWhenTokensDiffer() {
        assertFalse(InternalTokenUtils.matches("token-a", "token-b"));
    }
}
