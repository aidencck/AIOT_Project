package com.aiot.rule.client;

import com.aiot.common.api.Result;
import com.aiot.common.dto.ai.AiDeviceContextResp;
import com.aiot.common.dto.ai.AiKnowledgeSearchRequest;
import com.aiot.common.dto.ai.AiKnowledgeSearchResponse;
import com.aiot.common.dto.ai.AiRuntimeContextPayload;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Component
public class KnowledgeSearchClient {

    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    private final RestClient restClient;
    private final String internalToken;

    public KnowledgeSearchClient(
            RestClient.Builder restClientBuilder,
            @org.springframework.beans.factory.annotation.Value("${aiot.data-parser.base-url:lb://aiot-data-parser}") String dataParserBaseUrl,
            @org.springframework.beans.factory.annotation.Value("${aiot.internal.token:${AIOT_INTERNAL_TOKEN:}}") String internalToken) {
        this.restClient = restClientBuilder.clone().baseUrl(dataParserBaseUrl).build();
        this.internalToken = internalToken;
    }

    public AiKnowledgeSearchResponse search(AiDeviceContextResp context, String sceneType, String query) {
        if (context == null) {
            return AiKnowledgeSearchResponse.builder().chunks(java.util.List.of()).build();
        }
        return search(context.getProductKey(), context.getThingModelJson(), sceneType, query);
    }

    public AiKnowledgeSearchResponse search(AiRuntimeContextPayload contextPayload, String sceneType, String query) {
        if (contextPayload == null || contextPayload.getRuntimeContext() == null) {
            return AiKnowledgeSearchResponse.builder().chunks(java.util.List.of()).build();
        }
        return search(contextPayload.getRuntimeContext().getProductKey(), contextPayload.getThingModelJson(), sceneType, query);
    }

    public AiKnowledgeSearchResponse search(String productKey, String thingModelJson, String sceneType, String query) {
        if (!StringUtils.hasText(productKey) && !StringUtils.hasText(thingModelJson)) {
            return AiKnowledgeSearchResponse.builder().chunks(java.util.List.of()).build();
        }
        AiKnowledgeSearchRequest request = new AiKnowledgeSearchRequest();
        request.setProductKey(productKey);
        request.setThingModelJson(thingModelJson);
        request.setSceneType(sceneType);
        request.setQuery(query);
        request.setTopK(3);
        try {
            Result<AiKnowledgeSearchResponse> result = restClient.post()
                    .uri("/api/v1/internal/knowledge/search")
                    .header(INTERNAL_TOKEN_HEADER, internalToken)
                    .body(request)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Result<AiKnowledgeSearchResponse>>() {
                    });
            return result == null || result.getData() == null
                    ? AiKnowledgeSearchResponse.builder().chunks(java.util.List.of()).build()
                    : result.getData();
        } catch (Exception ex) {
            return AiKnowledgeSearchResponse.builder().chunks(java.util.List.of()).build();
        }
    }
}
