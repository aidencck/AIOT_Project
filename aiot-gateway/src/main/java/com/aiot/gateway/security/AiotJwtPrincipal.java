package com.aiot.gateway.security;

/**
 * 网关侧 JWT 解析出的可信用户信息（用于给下游透传 Header）。
 */
public record AiotJwtPrincipal(String userId, String globalUserId, String phone) {
}
