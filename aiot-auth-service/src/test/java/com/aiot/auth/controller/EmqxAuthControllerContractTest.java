package com.aiot.auth.controller;

import com.aiot.auth.service.AuthService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.InputStream;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@WebMvcTest(EmqxAuthController.class)
class EmqxAuthControllerContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @Test
    void auth_successShouldMatchContract() throws Exception {
        when(authService.authenticateDevice(any())).thenReturn(true);

        MvcResult result = mockMvc.perform(post("/api/v1/emqx/auth")
                        .contentType("application/json")
                        .content("""
                                {"clientid":"c1","username":"u1","password":"p1"}
                                """))
                .andReturn();

        Map<String, Object> contract = loadContract("contracts/emqx-auth-success.json");
        assertEquals(contract.get("httpStatus"), result.getResponse().getStatus());
        assertEquals(contract.get("body"), Objects.requireNonNull(result.getResponse().getContentAsString()));
    }

    @Test
    void auth_failureShouldMatchContract() throws Exception {
        when(authService.authenticateDevice(any())).thenReturn(false);

        MvcResult result = mockMvc.perform(post("/api/v1/emqx/auth")
                        .contentType("application/json")
                        .content("""
                                {"clientid":"c1","username":"u1","password":"p1"}
                                """))
                .andReturn();

        Map<String, Object> contract = loadContract("contracts/emqx-auth-failed.json");
        assertEquals(contract.get("httpStatus"), result.getResponse().getStatus());
        assertEquals(contract.get("body"), Objects.requireNonNull(result.getResponse().getContentAsString()));
    }

    private Map<String, Object> loadContract(String path) throws Exception {
        ClassPathResource resource = new ClassPathResource(Objects.requireNonNull(path));
        try (InputStream inputStream = resource.getInputStream()) {
            return objectMapper.readValue(inputStream, new TypeReference<>() {
            });
        }
    }
}
