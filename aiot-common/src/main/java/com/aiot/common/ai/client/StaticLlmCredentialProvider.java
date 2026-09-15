package com.aiot.common.ai.client;

import com.aiot.common.ai.config.AiLlmProperties;

/**
 * L0 静态凭证实现：直接读取 {@code ai.llm.api-key} 配置。
 *
 * <p>作为默认登录实现存在。当需要动态凭证（STS/Vault）时，新增实现类并在 Spring 容器中注册，
 * 即可通过 {@code @ConditionalOnMissingBean} 覆盖本默认实现，客户端零改动。
 */
public class StaticLlmCredentialProvider implements LlmCredentialProvider {

    private final AiLlmProperties properties;

    public StaticLlmCredentialProvider(AiLlmProperties properties) {
        this.properties = properties;
    }

    @Override
    public String resolveApiKey() {
        return properties.getApiKey();
    }
}
