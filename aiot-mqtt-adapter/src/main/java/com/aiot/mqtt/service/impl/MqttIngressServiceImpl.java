package com.aiot.mqtt.service.impl;

import com.aiot.common.api.Result;
import com.aiot.common.api.ResultCode;
import com.aiot.common.exception.BusinessException;
import com.aiot.common.http.CrossServiceHttpExecutor;
import com.aiot.mqtt.dto.MqttDispatchResponse;
import com.aiot.mqtt.dto.MqttIngressRequest;
import com.aiot.mqtt.service.MqttIngressService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@Service
public class MqttIngressServiceImpl implements MqttIngressService {

    private final RestTemplate restTemplate;
    private final CrossServiceHttpExecutor crossServiceHttpExecutor;
    private final String parserBaseUrl;
    private final String internalToken;

    public MqttIngressServiceImpl(
            RestTemplate restTemplate,
            CrossServiceHttpExecutor crossServiceHttpExecutor,
            @Value("${aiot.data-parser.base-url:lb://aiot-data-parser}") String parserBaseUrl,
            @Value("${aiot.internal.token:}") String internalToken) {
        this.restTemplate = restTemplate;
        this.crossServiceHttpExecutor = crossServiceHttpExecutor;
        this.parserBaseUrl = parserBaseUrl;
        this.internalToken = internalToken;
    }

    @Override
    public MqttDispatchResponse dispatchToParser(MqttIngressRequest request) {
        if (request == null || !StringUtils.hasText(request.getDeviceId()) || !StringUtils.hasText(request.getPayload())) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "deviceId 与 payload 不能为空");
        }
        if (!StringUtils.hasText(internalToken)) {
            throw new BusinessException(ResultCode.FAILED, "服务内部令牌未配置");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Token", internalToken);

        Map<String, Object> parserReq = new HashMap<>();
        parserReq.put("messageId", request.getMessageId());
        parserReq.put("deviceId", request.getDeviceId());
        parserReq.put("topic", request.getTopic());
        parserReq.put("payload", request.getPayload());
        parserReq.put("timestamp", request.getTimestamp());
        parserReq.put("source", "aiot-mqtt-adapter");
        // traceId 由 data-parser 优先使用入参，其次回落 MDC
        parserReq.put("traceId", org.slf4j.MDC.get("traceId"));

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(parserReq, headers);
        String url = parserBaseUrl + "/api/v1/internal/parser/messages";
        Result<Map<String, Object>> body = crossServiceHttpExecutor.execute(
                "aiot-data-parser.parseAndPublish",
                () -> Mono.fromCallable(() -> {
                    ResponseEntity<Result<Map<String, Object>>> response = restTemplate.exchange(
                            url,
                            HttpMethod.POST,
                            entity,
                            new ParameterizedTypeReference<Result<Map<String, Object>>>() {
                            }
                    );
                    return response.getBody();
                })
        );
        if (body == null || body.getCode() == null || body.getCode() != 200 || body.getData() == null) {
            throw new BusinessException(ResultCode.FAILED, "data-parser 返回异常");
        }

        MqttDispatchResponse dispatchResponse = new MqttDispatchResponse();
        dispatchResponse.setParserEventId(String.valueOf(body.getData().get("eventId")));
        dispatchResponse.setParserEventType(String.valueOf(body.getData().get("eventType")));
        dispatchResponse.setParserStreamKey(String.valueOf(body.getData().get("streamKey")));
        dispatchResponse.setDeviceId(String.valueOf(body.getData().get("deviceId")));
        return dispatchResponse;
    }
}
