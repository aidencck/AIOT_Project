package com.aiot.device.utils;

import com.aiot.common.api.ResultCode;
import com.aiot.common.exception.BusinessException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtUtilsTest {

    private static final String SECRET = "12345678901234567890123456789012";

    @Test
    void init_shouldThrowWhenSecretTooShort() {
        JwtUtils jwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(jwtUtils, "secret", "short-secret");

        assertThrows(IllegalArgumentException.class, jwtUtils::init);
    }

    @Test
    void parseToken_shouldReturnClaimsWhenValid() {
        JwtUtils jwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(jwtUtils, "secret", SECRET);
        jwtUtils.init();

        String token = Jwts.builder()
                .setSubject("u-1")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                .compact();

        Claims claims = jwtUtils.parseToken(token);

        assertEquals("u-1", claims.getSubject());
    }

    @Test
    void parseToken_shouldThrowUnauthorizedWhenExpired() {
        JwtUtils jwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(jwtUtils, "secret", SECRET);
        jwtUtils.init();

        String token = Jwts.builder()
                .setSubject("u-1")
                .setIssuedAt(new Date(System.currentTimeMillis() - 120_000))
                .setExpiration(new Date(System.currentTimeMillis() - 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                .compact();

        BusinessException ex = assertThrows(BusinessException.class, () -> jwtUtils.parseToken(token));
        assertEquals(ResultCode.UNAUTHORIZED, ex.getResultCode());
        assertEquals("Token 已过期", ex.getMessage());
    }

    @Test
    void parseToken_shouldThrowUnauthorizedWhenInvalid() {
        JwtUtils jwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(jwtUtils, "secret", SECRET);
        jwtUtils.init();

        BusinessException ex = assertThrows(BusinessException.class, () -> jwtUtils.parseToken("not-a-jwt"));
        assertEquals(ResultCode.UNAUTHORIZED, ex.getResultCode());
        assertEquals("无效的 Token", ex.getMessage());
    }
}
