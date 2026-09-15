package com.aiot.device.client;

import com.aiot.common.api.Result;
import com.aiot.common.dto.ai.AiKnowledgeRebuildRequest;
import com.aiot.common.dto.ai.AiKnowledgeRebuildResponse;
import com.aiot.common.http.CrossServiceHttpExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class KnowledgeRebuildClient {

    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    private final RestClient restClient;
    private final String internalToken;
    private final CrossServiceHttpExecutor executor;

    public KnowledgeRebuildClient(
            RestClient.Builder restClientBuilder,
            @org.springframework.beans.factory.annotation.Value("${aiot.data-parser.base-url:lb://aiot-data-parser}") String dataParserBaseUrl,
            @org.springframework.beans.factory.annotation.Value("${aiot.internal.token:${AIOT_INTERNAL_TOKEN:}}") String internalToken,
            CrossServiceHttpExecutor executor) {
        this.restClient = restClientBuilder.clone().baseUrl(dataParserBaseUrl).build();
        this.internalToken = internalToken;
        this.executor = executor;
    }

    public void rebuild(String productKey, String thingModelJson, String source) {
        AiKnowledgeRebuildRequest request = new AiKnowledgeRebuildRequest();
        request.setProductKey(productKey);
        request.setThingModelJson(thingModelJson);
        request.setSource(source);
        try {
            executor.executeBlocking("device-knowledge-rebuild", () -> restClient.post()
                    .uri("/api/v1/internal/knowledge/rebuild")
                    .header(INTERNAL_TOKEN_HEADER, internalToken)
                    .body(request)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Result<AiKnowledgeRebuildResponse>>() {
                    }));
        } catch (Exception ex) {
            log.warn("Failed to trigger knowledge rebuild, productKey={}", productKey, ex);
        }
    }
}
