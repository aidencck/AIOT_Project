package com.aiot.device.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeviceRespJsonTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldSerializeDeviceIdAlongsideIdForCompatibility() throws Exception {
        DeviceResp resp = new DeviceResp();
        resp.setId("dev-1");
        resp.setGlobalDeviceId("gdev-1");
        resp.setDeviceSn("sn-1");
        resp.setAuthIdentity("auth-1");

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsBytes(resp));

        assertEquals("dev-1", json.get("id").asText());
        assertEquals("dev-1", json.get("deviceId").asText());
        assertEquals("gdev-1", json.get("globalDeviceId").asText());
        assertEquals("sn-1", json.get("deviceSn").asText());
        assertEquals("auth-1", json.get("authIdentity").asText());
    }
}
