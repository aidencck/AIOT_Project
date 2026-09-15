package com.aiot.mqtt.controller;

import com.aiot.common.api.ResultCode;
import com.aiot.common.exception.BusinessException;
import com.aiot.mqtt.dto.MqttIngressRequest;
import com.aiot.mqtt.service.MqttIngressService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MqttIngressControllerAuthTest {

    @Test
    void shouldRejectWhenMissingInternalToken() {
        MqttIngressService service = req -> null;
        MqttIngressController controller = new MqttIngressController(service, "token-1");

        MqttIngressRequest request = new MqttIngressRequest();
        request.setDeviceId("d-1");
        request.setPayload("{\"status\":\"online\"}");

        BusinessException ex = assertThrows(BusinessException.class, () -> controller.ingest(request, null));
        assertEquals(ResultCode.UNAUTHORIZED, ex.getResultCode());
    }
}
