package com.aiot.common.security.jwt;

import com.aiot.common.exception.BusinessException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultAiotJwtServiceTest {

    @Test
    void shouldIssueAndVerifyUserToken() {
        DefaultAiotJwtService jwtService = new DefaultAiotJwtService(buildProperties());

        String token = jwtService.issueUserToken("u-1001", "gu-1001", "13800000000");
        Claims claims = jwtService.verify(token);

        assertEquals("u-1001", claims.getSubject());
        assertEquals("gu-1001", claims.get("global_user_id", String.class));
        assertEquals("13800000000", claims.get("phone", String.class));
        assertNotNull(claims.getId());
    }

    @Test
    void shouldRejectTokenWithoutJtiWhenRequired() {
        AiotJwtProperties properties = buildProperties();
        DefaultAiotJwtService jwtService = new DefaultAiotJwtService(properties);

        String token = Jwts.builder()
                .setSubject("u-1002")
                .setIssuer(properties.getIssuer())
                .setAudience(properties.getAudience())
                .setExpiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8)))
                .compact();

        BusinessException ex = assertThrows(BusinessException.class, () -> jwtService.verify(token));
        assertEquals("无效的 Token", ex.getMessage());
    }

    private AiotJwtProperties buildProperties() {
        AiotJwtProperties properties = new AiotJwtProperties();
        properties.setSecret("0123456789abcdef0123456789abcdef");
        properties.setIssuer("aiot");
        properties.setAudience("aiot-clients");
        properties.setRequireJti(true);
        properties.setClockSkewSeconds(60L);
        properties.setExpiration(3600_000L);
        return properties;
    }
}
