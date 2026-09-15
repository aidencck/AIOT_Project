package com.aiot.device.controller;

import com.aiot.common.config.GlobalResponseHandler;
import com.aiot.common.config.ResponseContractResolver;
import com.aiot.common.exception.GlobalExceptionHandler;
import com.aiot.device.dto.ProvisionResp;
import com.aiot.device.service.ProvisionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProvisionControllerIntegrationTest {

    private MockMvc mockMvc;

    private ProvisionService provisionService;

    @BeforeEach
    void setUp() {
        provisionService = mock(ProvisionService.class);
        ProvisionController provisionController = new ProvisionController();
        ReflectionTestUtils.setField(provisionController, "provisionService", provisionService);

        ObjectMapper objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders.standaloneSetup(provisionController)
                .setControllerAdvice(new GlobalExceptionHandler(),
                        new GlobalResponseHandler(objectMapper, new ResponseContractResolver()))
                .build();
    }

    @Test
    void createProvisionToken_shouldReturnWrappedResult() throws Exception {
        when(provisionService.generateProvisionToken("pk-1", "sn-1", "dn-1", "home-1")).thenReturn("token-1");

        mockMvc.perform(post("/api/v1/provision/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productKey": "pk-1",
                                  "deviceSn": "sn-1",
                                  "deviceName": "dn-1",
                                  "homeId": "home-1"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("token-1"));
    }

    @Test
    void getProvisionTokenCompat_shouldReturnWrappedResult() throws Exception {
        when(provisionService.generateProvisionToken("pk-2", "sn-2", "dn-2", "home-2")).thenReturn("token-2");

        mockMvc.perform(get("/api/v1/provision/token")
                        .queryParam("productKey", "pk-2")
                        .queryParam("deviceSn", "sn-2")
                        .queryParam("deviceName", "dn-2")
                        .queryParam("homeId", "home-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("token-2"));
    }

    @Test
    void exchangeToken_shouldReturnWrappedProvisionResp() throws Exception {
        ProvisionResp resp = new ProvisionResp();
        resp.setDeviceId("dev-1");
        resp.setGlobalDeviceId("gdev-1");
        resp.setDeviceSn("sn-1");
        resp.setAuthIdentity("auth-1");
        resp.setDeviceSecret("secret-1");
        resp.setMqttHost("mqtt.local");
        resp.setMqttPort(1883);
        when(provisionService.provisionDevice(any())).thenReturn(resp);

        mockMvc.perform(post("/api/v1/provision/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productKey": "pk-1",
                                  "deviceName": "dn-1",
                                  "globalDeviceId": "gdev-1",
                                  "deviceSn": "sn-1",
                                  "authIdentity": "auth-1",
                                  "provisionToken": "token-1"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.deviceId").value("dev-1"))
                .andExpect(jsonPath("$.data.globalDeviceId").value("gdev-1"))
                .andExpect(jsonPath("$.data.deviceSn").value("sn-1"))
                .andExpect(jsonPath("$.data.authIdentity").value("auth-1"))
                .andExpect(jsonPath("$.data.mqttHost").value("mqtt.local"))
                .andExpect(jsonPath("$.data.mqttPort").value(1883));
    }

    @Test
    void createProvisionToken_shouldRejectInvalidRequest() throws Exception {
        mockMvc.perform(post("/api/v1/provision/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productKey": "",
                                  "deviceSn": "sn-1",
                                  "homeId": "home-1"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("productKey 不能为空"));
    }

    @Test
    void exchangeToken_shouldRejectMissingProvisionToken() throws Exception {
        mockMvc.perform(post("/api/v1/provision/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productKey": "pk-1",
                                  "deviceSn": "sn-1",
                                  "provisionToken": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("provisionToken 不能为空"));
    }

    @Test
    void createProvisionToken_shouldSupportDeviceSnOnly() throws Exception {
        when(provisionService.generateProvisionToken("pk-3", "sn-only", null, "home-3")).thenReturn("token-3");

        mockMvc.perform(post("/api/v1/provision/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productKey": "pk-3",
                                  "deviceSn": "sn-only",
                                  "homeId": "home-3"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("token-3"));
    }

    @Test
    void exchangeToken_shouldRejectWhenDeviceIdentityMissing() throws Exception {
        mockMvc.perform(post("/api/v1/provision/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productKey": "pk-1",
                                  "provisionToken": "token-1"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("deviceSn 或 deviceName 至少传一个"));
    }
}
