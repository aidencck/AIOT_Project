package com.aiot.common.ai.client;

import com.aiot.common.ai.config.AiLlmProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiCompatibleLlmClientTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void shouldReportDisabledWhenSwitchOrCredentialsAreMissing() {
        AiLlmProperties properties = new AiLlmProperties();
        properties.setEnabled(false);
        properties.setBaseUrl("http://localhost:11434");
        properties.setApiKey("key");
        OpenAiCompatibleLlmClient disabledClient = client(properties);
        assertFalse(disabledClient.isEnabled());
        assertEquals("", disabledClient.chatJson("system", "user"));

        properties.setEnabled(true);
        properties.setApiKey(null);
        OpenAiCompatibleLlmClient missingApiKeyClient = client(properties);
        assertFalse(missingApiKeyClient.isEnabled());

        properties.setApiKey("key");
        properties.setBaseUrl(" ");
        OpenAiCompatibleLlmClient missingBaseUrlClient = client(properties);
        assertFalse(missingBaseUrlClient.isEnabled());
    }

    @Test
    void shouldResolveChatCompletionUrlVariants() {
        AiLlmProperties trailingSlashProperties = enabledProperties("http://localhost:11434/");
        OpenAiCompatibleLlmClient trailingSlashClient = client(trailingSlashProperties);
        assertEquals("http://localhost:11434/v1/chat/completions",
                ReflectionTestUtils.invokeMethod(trailingSlashClient, "resolveChatCompletionsUrl"));

        AiLlmProperties rawBaseProperties = enabledProperties("http://localhost:11434");
        OpenAiCompatibleLlmClient rawBaseClient = client(rawBaseProperties);
        assertEquals("http://localhost:11434/v1/chat/completions",
                ReflectionTestUtils.invokeMethod(rawBaseClient, "resolveChatCompletionsUrl"));

        AiLlmProperties directProperties = enabledProperties("http://localhost:11434/v1/chat/completions");
        OpenAiCompatibleLlmClient directClient = client(directProperties);
        assertEquals("http://localhost:11434/v1/chat/completions",
                ReflectionTestUtils.invokeMethod(directClient, "resolveChatCompletionsUrl"));
        assertTrue(directClient.isEnabled());
    }

    @Test
    void shouldReadStaticApiKeyFromProperties() {
        AiLlmProperties properties = enabledProperties("http://localhost:11434");
        assertEquals("api-key", new StaticLlmCredentialProvider(properties).resolveApiKey());
    }

    @Test
    void shouldUseCredentialProviderInsteadOfStaticPropertiesKey() {
        AiLlmProperties properties = enabledProperties("http://localhost:11434");
        properties.setApiKey(null);
        LlmCredentialProvider dynamic = () -> "dynamic-token";
        OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient(properties, dynamic, OBJECT_MAPPER);
        assertTrue(client.isEnabled());
    }

    private OpenAiCompatibleLlmClient client(AiLlmProperties properties) {
        return new OpenAiCompatibleLlmClient(properties, new StaticLlmCredentialProvider(properties), OBJECT_MAPPER);
    }

    private AiLlmProperties enabledProperties(String baseUrl) {
        AiLlmProperties properties = new AiLlmProperties();
        properties.setEnabled(true);
        properties.setBaseUrl(baseUrl);
        properties.setApiKey("api-key");
        properties.setModel("gpt-4o-mini");
        properties.setTimeoutMs(100L);
        return properties;
    }
}
