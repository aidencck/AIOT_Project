package com.aiot.common.security.jwt;

import com.aiot.common.api.ResultCode;
import com.aiot.common.exception.BusinessException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

final class DefaultAiotJwtService implements AiotJwtService {

    private final AiotJwtProperties properties;

    DefaultAiotJwtService(AiotJwtProperties properties) {
        this.properties = properties;
    }

    @Override
    public String issueUserToken(String userId, String globalUserId, String phone) {
        Long expirationMs = properties.getExpiration();
        if (expirationMs == null || expirationMs <= 0) {
            throw new IllegalArgumentException("aiot.security.jwt.expiration is required and must be > 0 when issuing tokens.");
        }
        if (!StringUtils.hasText(userId)) {
            throw new IllegalArgumentException("userId is required.");
        }
        String resolvedGlobalUserId = StringUtils.hasText(globalUserId) ? globalUserId : userId;

        Date now = new Date();
        Date exp = new Date(System.currentTimeMillis() + expirationMs);

        var builder = Jwts.builder()
                .setSubject(userId)
                .setId(UUID.randomUUID().toString())
                .claim("global_user_id", resolvedGlobalUserId)
                .claim("phone", phone)
                .setIssuedAt(now)
                .setExpiration(exp)
                .signWith(getSigningKey(), SignatureAlgorithm.HS256);

        if (StringUtils.hasText(properties.getIssuer())) {
            builder.setIssuer(properties.getIssuer());
        }
        if (StringUtils.hasText(properties.getAudience())) {
            builder.setAudience(properties.getAudience());
        }

        return builder.compact();
    }

    @Override
    public Claims verify(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .setAllowedClockSkewSeconds(properties.getClockSkewSeconds())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            if (!isClaimsValid(claims)) {
                throw new BusinessException(ResultCode.UNAUTHORIZED, "无效的 Token");
            }
            return claims;
        } catch (ExpiredJwtException e) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "Token 已过期");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "无效的 Token");
        }
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    private boolean isClaimsValid(Claims claims) {
        if (claims == null || !StringUtils.hasText(claims.getSubject())) {
            return false;
        }
        if (properties.isRequireJti() && !StringUtils.hasText(claims.getId())) {
            return false;
        }
        if (StringUtils.hasText(properties.getIssuer()) && !properties.getIssuer().equals(claims.getIssuer())) {
            return false;
        }
        if (StringUtils.hasText(properties.getAudience()) && !properties.getAudience().equals(claims.getAudience())) {
            return false;
        }
        return true;
    }
}
