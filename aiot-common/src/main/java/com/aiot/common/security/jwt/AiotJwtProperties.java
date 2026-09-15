package com.aiot.common.security.jwt;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * JWT properties shared across services.
 *
 * Config prefix: aiot.security.jwt
 */
@Data
@Validated
@ConfigurationProperties(prefix = "aiot.security.jwt")
public class AiotJwtProperties {

    @NotBlank
    @Size(min = 32)
    private String secret;

    /**
     * Optional issuer check.
     */
    private String issuer = "";

    /**
     * Optional audience check.
     */
    private String audience = "";

    /**
     * Whether to require a non-empty JTI claim.
     */
    private boolean requireJti = true;

    /**
     * Allowed clock skew seconds when parsing.
     */
    private long clockSkewSeconds = 60L;

    /**
     * Token expiration in milliseconds (used by issuer; optional for verifier-only services).
     *
     * Backward compatible with existing config: aiot.security.jwt.expiration
     */
    private Long expiration;
}
