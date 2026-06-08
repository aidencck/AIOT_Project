package com.aiot.gateway.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;

/**
 * JWT 鉴权前的占位 Authentication：仅携带原始 Token（credentials）。
 */
public class JwtPreAuthToken extends AbstractAuthenticationToken {

    private final String token;

    public JwtPreAuthToken(String token) {
        super(null);
        this.token = token;
        setAuthenticated(false);
    }

    @Override
    public Object getCredentials() {
        return token;
    }

    @Override
    public Object getPrincipal() {
        return null;
    }
}

