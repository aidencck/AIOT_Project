package com.aiot.rule.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;

@Configuration
@EnableConfigurationProperties(AiPersistenceMysqlProperties.class)
public class AiPersistenceMysqlConfig {

    @Bean(name = "aiMysqlDataSource")
    @ConditionalOnProperty(prefix = "aiot.ai.persistence.mysql", name = "enabled", havingValue = "true")
    public DataSource aiMysqlDataSource(AiPersistenceMysqlProperties properties) {
        if (!StringUtils.hasText(properties.getUrl())) {
            throw new IllegalStateException("aiot.ai.persistence.mysql.url must not be empty when mysql persistence is enabled");
        }
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(properties.getUrl());
        dataSource.setUsername(properties.getUsername());
        dataSource.setPassword(properties.getPassword());
        dataSource.setDriverClassName(properties.getDriverClassName());
        dataSource.setPoolName("ai-rule-mysql-writer");
        dataSource.setMaximumPoolSize(2);
        dataSource.setMinimumIdle(0);
        return dataSource;
    }

    @Bean(name = "aiMysqlJdbcTemplate")
    @ConditionalOnBean(name = "aiMysqlDataSource")
    public JdbcTemplate aiMysqlJdbcTemplate(DataSource aiMysqlDataSource) {
        return new JdbcTemplate(aiMysqlDataSource);
    }
}
