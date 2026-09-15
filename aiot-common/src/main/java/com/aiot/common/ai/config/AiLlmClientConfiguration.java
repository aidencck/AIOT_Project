package com.aiot.common.ai.config;

import com.aiot.common.ai.client.LlmClient;
import com.aiot.common.ai.client.LlmCredentialProvider;
import com.aiot.common.ai.client.OpenAiCompatibleLlmClient;
import com.aiot.common.ai.client.StaticLlmCredentialProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LLM 客户端装配（动态操作层）。
 *
 * <p>通过 {@code ai.llm.provider} 决定注册哪个 {@link LlmClient} 实现，实现第三方 LLM
 * Provider 的可剥离切换。默认 {@code openai-compatible}；后续新增 {@code ollama} / {@code bedrock}
 * 等适配器时，仅需新增一个带 {@code @ConditionalOnProperty(havingValue = "...")} 的 Bean。
 *
 * <p>凭证（登录层）默认使用 {@link StaticLlmCredentialProvider}；业务方只需注册自定义
 * {@link LlmCredentialProvider} Bean 即可替换为动态凭证，无需改动客户端。
 */
@Configuration
public class AiLlmClientConfiguration {

    @Bean
    @ConditionalOnMissingBean(LlmCredentialProvider.class)
    public LlmCredentialProvider staticLlmCredentialProvider(AiLlmProperties properties) {
        return new StaticLlmCredentialProvider(properties);
    }

    @Bean
    @ConditionalOnProperty(name = "ai.llm.provider", havingValue = "openai-compatible", matchIfMissing = true)
    public LlmClient openAiCompatibleLlmClient(AiLlmProperties properties,
                                               LlmCredentialProvider credentialProvider,
                                               ObjectMapper objectMapper) {
        return new OpenAiCompatibleLlmClient(properties, credentialProvider, objectMapper);
    }
}
