package com.aiot.common.security.jwt;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(AiotJwtProperties.class)
@ConditionalOnProperty(prefix = "aiot.security.jwt", name = "secret")
public class AiotJwtAutoConfiguration {

    @Bean
    public AiotJwtService aiotJwtService(AiotJwtProperties properties) {
        return new DefaultAiotJwtService(properties);
    }
}
