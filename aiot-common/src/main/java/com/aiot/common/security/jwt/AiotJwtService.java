package com.aiot.common.security.jwt;

import io.jsonwebtoken.Claims;

/**
 * Unified JWT issuing + verification service.
 */
public interface AiotJwtService {

    /**
     * Issues a user JWT token with subject=userId and claim "phone".
     * Must include JTI to satisfy gateway default validation.
     */
    String issueUserToken(String userId, String phone);

    /**
     * Parses and validates a token; returns claims if valid.
     *
     * Throws BusinessException(ResultCode.UNAUTHORIZED) on invalid/expired token.
     */
    Claims verify(String token);
}

