package com.aiot.common.security.jwt;

/**
 * Extracts JWT token from Authorization header.
 */
public final class BearerTokenResolver {

    private static final String BEARER_PREFIX = "Bearer ";

    private BearerTokenResolver() {
    }

    public static String resolveFromAuthorizationHeader(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        if (!authorization.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = authorization.substring(BEARER_PREFIX.length());
        return token.isBlank() ? null : token;
    }
}

