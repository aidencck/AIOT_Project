package com.aiot.rule.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "aiot.ai.persistence.mysql")
public class AiPersistenceMysqlProperties {
    private boolean enabled = false;
    private String readMode = "REDIS";
    private boolean cutoverGuardEnabled = true;
    private boolean allowUnsafeMysqlRead = false;
    private long cutoverGuardCacheTtlMs = 5000L;
    private String url;
    private String username;
    private String password;
    private String driverClassName = "com.mysql.cj.jdbc.Driver";

    public AiPersistenceReadMode resolvedReadMode() {
        return AiPersistenceReadMode.from(readMode);
    }
}
