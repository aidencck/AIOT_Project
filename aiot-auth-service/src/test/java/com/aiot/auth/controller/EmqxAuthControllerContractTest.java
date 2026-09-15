package com.aiot.auth.controller;

import com.aiot.auth.service.AuthMetrics;
import com.aiot.auth.service.AuthService;
import com.aiot.common.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.InputStream;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class EmqxAuthControllerContractTest {

    private MockMvc mockMvc;

    private ObjectMapper objectMapper;

    private AuthService authService;

    private AuthMetrics authMetrics;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        authMetrics = mock(AuthMetrics.class);
        EmqxAuthController controller = new EmqxAuthController(authService, authMetrics);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();
    }

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

    @Test
    void auth_invalidRequestShouldDenyByContract() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/emqx/auth")
                        .contentType("application/json")
                        .content("""
                                {"clientid":"","username":"u1","password":"p1"}
                                """))
                .andReturn();

        assertEquals(200, result.getResponse().getStatus());
        assertEquals("deny", result.getResponse().getContentAsString());
        verifyNoInteractions(authService);
    }

    private Map<String, Object> loadContract(String path) throws Exception {
        ClassPathResource resource = new ClassPathResource(Objects.requireNonNull(path));
        try (InputStream inputStream = resource.getInputStream()) {
            return objectMapper.readValue(inputStream, new TypeReference<>() {
            });
        }
    }
}
