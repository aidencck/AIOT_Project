package com.aiot.mqtt.service.impl;

import com.aiot.common.api.Result;
import com.aiot.common.api.ResultCode;
import com.aiot.common.exception.BusinessException;
import com.aiot.common.http.CrossServiceHttpExecutor;
import com.aiot.mqtt.dto.MqttDispatchResponse;
import com.aiot.mqtt.dto.MqttIngressRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

class MqttIngressServiceImplTest {

    private final RestTemplate restTemplate = mock(RestTemplate.class);
    private final CrossServiceHttpExecutor crossServiceHttpExecutor = mock(CrossServiceHttpExecutor.class);

    private MqttIngressServiceImpl newService(String internalToken) {
        return new MqttIngressServiceImpl(
                restTemplate,
                crossServiceHttpExecutor,
                "lb://aiot-data-parser",
                internalToken
        );
    }

    private MqttIngressRequest newRequest() {
        MqttIngressRequest request = new MqttIngressRequest();
        request.setDeviceId("d-1");
        request.setPayload("{\"status\":\"online\"}");
        return request;
    }

    private void stubParserResult(Result<Map<String, Object>> result) {
        doReturn(result).when(crossServiceHttpExecutor).execute(
                eq("aiot-data-parser.parseAndPublish"),
                org.mockito.ArgumentMatchers.<Supplier<Mono<Result<Map<String, Object>>>>>any()
        );
    }

    @Test
    void shouldRejectWhenRequestIsNull() {
        MqttIngressServiceImpl service = newService("token-1");

        assertThatThrownBy(() -> service.dispatchToParser(null))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getResultCode()).isEqualTo(ResultCode.VALIDATE_FAILED);
                    assertThat(ex.getMessage()).contains("deviceId 与 payload 不能为空");
                });
    }

    @Test
    void shouldRejectWhenDeviceIdIsBlank() {
        MqttIngressServiceImpl service = newService("token-1");
        MqttIngressRequest request = newRequest();
        request.setDeviceId("");

        assertThatThrownBy(() -> service.dispatchToParser(request))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getResultCode()).isEqualTo(ResultCode.VALIDATE_FAILED);
                    assertThat(ex.getMessage()).contains("deviceId 与 payload 不能为空");
                });
    }

    @Test
    void shouldRejectWhenPayloadIsBlank() {
        MqttIngressServiceImpl service = newService("token-1");
        MqttIngressRequest request = newRequest();
        request.setPayload("");

        assertThatThrownBy(() -> service.dispatchToParser(request))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getResultCode()).isEqualTo(ResultCode.VALIDATE_FAILED);
                    assertThat(ex.getMessage()).contains("deviceId 与 payload 不能为空");
                });
    }

    @Test
    void shouldRejectWhenInternalTokenIsMissing() {
        MqttIngressServiceImpl service = newService("");

        assertThatThrownBy(() -> service.dispatchToParser(newRequest()))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getResultCode()).isEqualTo(ResultCode.FAILED);
                    assertThat(ex.getMessage()).contains("令牌未配置");
                });
    }

    @Test
    void shouldReturnDispatchResponseOnSuccess() {
        MqttIngressServiceImpl service = newService("token-1");
        Map<String, Object> data = new HashMap<>();
        data.put("eventId", "event-1");
        data.put("eventType", "status");
        data.put("streamKey", "stream-1");
        data.put("deviceId", "d-1");
        Result<Map<String, Object>> result = new Result<>();
        result.setCode(200);
        result.setData(data);
        stubParserResult(result);

        MqttDispatchResponse response = service.dispatchToParser(newRequest());

        assertThat(response.getParserEventId()).isEqualTo("event-1");
        assertThat(response.getParserEventType()).isEqualTo("status");
        assertThat(response.getParserStreamKey()).isEqualTo("stream-1");
        assertThat(response.getDeviceId()).isEqualTo("d-1");
    }

    @Test
    void shouldRejectWhenParserReturnsNullBody() {
        MqttIngressServiceImpl service = newService("token-1");
        stubParserResult(null);

        assertThatThrownBy(() -> service.dispatchToParser(newRequest()))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getResultCode()).isEqualTo(ResultCode.FAILED);
                    assertThat(ex.getMessage()).contains("data-parser 返回异常");
                });
    }

    @Test
    void shouldRejectWhenParserReturnsNon200Code() {
        MqttIngressServiceImpl service = newService("token-1");
        Result<Map<String, Object>> result = new Result<>();
        result.setCode(500);
        result.setData(new HashMap<>());
        stubParserResult(result);

        assertThatThrownBy(() -> service.dispatchToParser(newRequest()))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getResultCode()).isEqualTo(ResultCode.FAILED);
                    assertThat(ex.getMessage()).contains("data-parser 返回异常");
                });
    }

    @Test
    void shouldRejectWhenParserReturnsNullData() {
        MqttIngressServiceImpl service = newService("token-1");
        Result<Map<String, Object>> result = new Result<>();
        result.setCode(200);
        result.setData(null);
        stubParserResult(result);

        assertThatThrownBy(() -> service.dispatchToParser(newRequest()))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getResultCode()).isEqualTo(ResultCode.FAILED);
                    assertThat(ex.getMessage()).contains("data-parser 返回异常");
                });
    }
}
