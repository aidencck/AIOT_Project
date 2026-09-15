package com.aiot.common.security.jwt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiotJwtPropertiesTest {

    @Test
    void shouldSupportAccessorsEqualsHashCodeAndToString() {
        AiotJwtProperties left = new AiotJwtProperties();
        left.setSecret("0123456789abcdef0123456789abcdef");
        left.setIssuer("issuer-a");
        left.setAudience("audience-a");
        left.setRequireJti(false);
        left.setClockSkewSeconds(30L);
        left.setExpiration(60000L);

        AiotJwtProperties right = new AiotJwtProperties();
        right.setSecret("0123456789abcdef0123456789abcdef");
        right.setIssuer("issuer-a");
        right.setAudience("audience-a");
        right.setRequireJti(false);
        right.setClockSkewSeconds(30L);
        right.setExpiration(60000L);

        assertEquals("issuer-a", left.getIssuer());
        assertEquals("audience-a", left.getAudience());
        assertEquals(60000L, left.getExpiration());
        assertEquals(left, right);
        assertEquals(left.hashCode(), right.hashCode());
        assertTrue(left.toString().contains("issuer=issuer-a"));

        right.setAudience("audience-b");
        assertNotEquals(left, right);
    }
}
