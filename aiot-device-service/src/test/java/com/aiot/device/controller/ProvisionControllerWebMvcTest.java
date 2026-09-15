package com.aiot.device.controller;

import com.aiot.common.config.GlobalResponseHandler;
import com.aiot.common.config.ResponseContractResolver;
import com.aiot.common.exception.GlobalExceptionHandler;
import com.aiot.device.config.AuthInterceptor;
import com.aiot.device.config.WebMvcConfig;
import com.aiot.device.dto.ProvisionResp;
import com.aiot.device.service.ProvisionService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ProvisionController.class, properties = {
        "AIOT_JWT_SECRET=0123456789abcdef0123456789abcdef"
})
@ContextConfiguration(classes = ProvisionControllerWebMvcTest.MvcSliceConfig.class)
class ProvisionControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProvisionService provisionService;

    @Test
    void shouldWrapProvisionTokenRequestThroughSpringMvcSlice() throws Exception {
        when(provisionService.generateProvisionToken("pk-1", "sn-1", "dn-1", "home-1")).thenReturn("token-1");

        mockMvc.perform(post("/api/v1/provision/token")
                        .header("X-User-Id", "u-1")
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
    void shouldRejectTokenRequestWithoutGatewayIdentity() throws Exception {
        mockMvc.perform(post("/api/v1/provision/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productKey": "pk-1",
                                  "deviceName": "dn-1",
                                  "homeId": "home-1"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void shouldAllowExchangeWithoutGatewayHeaderBecausePathIsExcluded() throws Exception {
        ProvisionResp resp = new ProvisionResp();
        resp.setDeviceId("dev-1");
        resp.setGlobalDeviceId("gdev-1");
        resp.setDeviceSn("sn-1");
        when(provisionService.provisionDevice(ArgumentMatchers.any())).thenReturn(resp);

        mockMvc.perform(post("/api/v1/provision/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productKey": "pk-1",
                                  "deviceName": "dn-1",
                                  "provisionToken": "token-1"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.deviceId").value("dev-1"));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({
            ProvisionController.class,
            WebMvcConfig.class,
            AuthInterceptor.class,
            GlobalExceptionHandler.class,
            GlobalResponseHandler.class,
            ResponseContractResolver.class
    })
    static class MvcSliceConfig {
    }
}
