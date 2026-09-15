package com.aiot.common.ai.client;

import com.aiot.common.ai.config.AiLlmProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class OpenAiCompatibleLlmClient implements LlmClient {

    private final AiLlmProperties properties;
    private final LlmCredentialProvider credentialProvider;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public OpenAiCompatibleLlmClient(AiLlmProperties properties,
                                     LlmCredentialProvider credentialProvider,
                                     ObjectMapper objectMapper) {
        this.properties = properties;
        this.credentialProvider = credentialProvider;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {{
                    setConnectTimeout(Duration.ofMillis(properties.getTimeoutMs()));
                    setReadTimeout(Duration.ofMillis(properties.getTimeoutMs()));
                }})
                .build();
    }

    @Override
    public String chatJson(String systemPrompt, String userPrompt) {
        if (!isEnabled()) {
            return "";
        }
        try {
            // 以 String 读取响应体，规避 Ollama 返回 application/octet-stream 等
            // 非标准 Content-Type 导致 Jackson 无法按 JsonNode 反序列化的问题。
            Map<String, Object> body = new HashMap<>();
            body.put("model", properties.getModel());
            body.put("temperature", properties.getTemperature());
            body.put("response_format", Map.of("type", "json_object"));
            body.put("messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userPrompt)
            ));
            Map<String, Object> options = new HashMap<>();
            if (properties.getNumPredict() != null) {
                options.put("num_predict", properties.getNumPredict());
            }
            if (properties.getNumCtx() != null) {
                options.put("num_ctx", properties.getNumCtx());
            }
            if (!options.isEmpty()) {
                body.put("options", options);
            }
            String raw = restClient.post()
                    .uri(resolveChatCompletionsUrl())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + credentialProvider.resolveApiKey())
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            if (raw == null || raw.isBlank()) {
                return "";
            }
            JsonNode responseBody = objectMapper.readTree(raw);
            JsonNode contentNode = responseBody.path("choices").path(0).path("message").path("content");
            if (contentNode.isMissingNode() || contentNode.isNull()) {
                log.warn("LLM 返回结果缺少 content 字段");
                return "";
            }
            return contentNode.asText("");
        } catch (Exception ex) {
            log.warn("LLM 调用失败，降级为确定性兜底: {}", ex.getMessage());
            return "";
        }
    }

    @Override
    public boolean isEnabled() {
        return properties.isEnabled()
                && StringUtils.hasText(properties.getBaseUrl())
                && StringUtils.hasText(credentialProvider.resolveApiKey());
    }

    @Override
    public String getModelName() {
        return properties.getModel();
    }

    private String resolveChatCompletionsUrl() {
        String baseUrl = properties.getBaseUrl().trim();
        if (baseUrl.endsWith("/v1/chat/completions")) {
            return baseUrl;
        }
        if (baseUrl.endsWith("/")) {
            return baseUrl + "v1/chat/completions";
        }
        return baseUrl + "/v1/chat/completions";
    }
}
