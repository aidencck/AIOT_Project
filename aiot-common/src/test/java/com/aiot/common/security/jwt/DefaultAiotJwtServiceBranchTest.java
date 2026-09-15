package com.aiot.common.security.jwt;

import com.aiot.common.exception.BusinessException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultAiotJwtServiceBranchTest {

    @Test
    void shouldFallbackGlobalUserIdToUserIdWhenBlank() {
        DefaultAiotJwtService jwtService = new DefaultAiotJwtService(buildProperties());

        String token = jwtService.issueUserToken("u-2001", "", "13800001111");
        Claims claims = jwtService.verify(token);

        assertEquals("u-2001", claims.getSubject());
        assertEquals("u-2001", claims.get("global_user_id", String.class));
    }

    @Test
    void shouldRejectTokenWhenIssuerDoesNotMatch() {
        AiotJwtProperties properties = buildProperties();
        DefaultAiotJwtService jwtService = new DefaultAiotJwtService(properties);

        String token = Jwts.builder()
                .setSubject("u-issuer")
                .setId(UUID.randomUUID().toString())
                .setIssuer("other-issuer")
                .setAudience(properties.getAudience())
                .setExpiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8)),
                        SignatureAlgorithm.HS256)
                .compact();

        BusinessException ex = assertThrows(BusinessException.class, () -> jwtService.verify(token));
        assertEquals("无效的 Token", ex.getMessage());
    }

    @Test
    void shouldRejectTokenWhenAudienceDoesNotMatch() {
        AiotJwtProperties properties = buildProperties();
        DefaultAiotJwtService jwtService = new DefaultAiotJwtService(properties);

        String token = Jwts.builder()
                .setSubject("u-audience")
                .setId(UUID.randomUUID().toString())
                .setIssuer(properties.getIssuer())
                .setAudience("other-audience")
                .setExpiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8)),
                        SignatureAlgorithm.HS256)
                .compact();

        BusinessException ex = assertThrows(BusinessException.class, () -> jwtService.verify(token));
        assertEquals("无效的 Token", ex.getMessage());
    }

    @Test
    void shouldRejectExpiredToken() {
        AiotJwtProperties properties = buildProperties();
        properties.setClockSkewSeconds(0L);
        DefaultAiotJwtService jwtService = new DefaultAiotJwtService(properties);

        String token = Jwts.builder()
                .setSubject("u-expired")
                .setId(UUID.randomUUID().toString())
                .setIssuer(properties.getIssuer())
                .setAudience(properties.getAudience())
                .setExpiration(Date.from(Instant.now().minusSeconds(5)))
                .signWith(Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8)),
                        SignatureAlgorithm.HS256)
                .compact();

        BusinessException ex = assertThrows(BusinessException.class, () -> jwtService.verify(token));
        assertEquals("Token 已过期", ex.getMessage());
    }

    @Test
    void shouldRejectIssuingTokenWhenExpirationIsNotPositive() {
        AiotJwtProperties properties = buildProperties();
        properties.setExpiration(0L);
        DefaultAiotJwtService jwtService = new DefaultAiotJwtService(properties);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> jwtService.issueUserToken("u-invalid-exp", "gu-invalid-exp", "13800002222"));

        assertEquals("aiot.security.jwt.expiration is required and must be > 0 when issuing tokens.", ex.getMessage());
    }

    @Test
    void shouldRejectIssuingTokenWhenUserIdIsBlank() {
        DefaultAiotJwtService jwtService = new DefaultAiotJwtService(buildProperties());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> jwtService.issueUserToken(" ", "gu-blank", "13800003333"));

        assertEquals("userId is required.", ex.getMessage());
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
