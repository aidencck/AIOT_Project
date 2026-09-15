package com.aiot.auth.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class SignUtilsTest {

    @Test
    void signWithHmacSha256_shouldGenerateExpectedSignature() {
        String data = "client1:user1:1700000000";
        String secret = "secret-key";

        String signature = SignUtils.signWithHmacSha256(data, secret);

        assertEquals("a2626c6778353f46d87deb789ab72b22c8d43ed92f1d90afa5e0fe5a48af3448", signature);
    }

    @Test
    void signWithHmacSha256_shouldChangeWhenSecretChanges() {
        String data = "client1:user1:1700000000";

        String sigA = SignUtils.signWithHmacSha256(data, "secret-key-a");
        String sigB = SignUtils.signWithHmacSha256(data, "secret-key-b");

        assertNotEquals(sigA, sigB);
    }
}
