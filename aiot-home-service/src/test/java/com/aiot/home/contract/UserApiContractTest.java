package com.aiot.home.contract;

import com.aiot.common.config.ResponseContractResolver;
import com.aiot.common.config.GlobalResponseHandler;
import com.aiot.common.exception.GlobalExceptionHandler;
import com.aiot.home.controller.UserController;
import com.aiot.home.dto.LoginResp;
import com.aiot.home.service.UserService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class UserApiContractTest {

    private MockMvc mockMvc;

    private ObjectMapper objectMapper;

    private UserService userService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        userService = mock(UserService.class);
        UserController userController = new UserController();
        ReflectionTestUtils.setField(userController, "userService", userService);

        mockMvc = MockMvcBuilders.standaloneSetup(userController)
                .setControllerAdvice(new GlobalExceptionHandler(),
                        new GlobalResponseHandler(objectMapper, new ResponseContractResolver()))
                .build();
    }

    @Test
    void login_successResponseShouldMatchContract() throws Exception {
        LoginResp resp = new LoginResp();
        resp.setToken("contract-token");
        resp.setUserId("u-100");
        resp.setNickname("contract-user");
        when(userService.login(any())).thenReturn(resp);

        MvcResult result = mockMvc.perform(post("/api/v1/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"13800138000","password":"123456"}
                                """))
                .andReturn();

        Map<String, Object> contract = loadContract("contracts/user-login-success.json");
        assertEquals(contract.get("httpStatus"), result.getResponse().getStatus());
        assertRequiredPaths(result.getResponse().getContentAsString(),
                castPathList(contract.get("requiredJsonPaths")));
    }

    @Test
    void login_validateErrorShouldMatchContract() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"bad","password":"123456"}
                                """))
                .andReturn();

        Map<String, Object> contract = loadContract("contracts/user-login-validate-failed.json");
        assertEquals(contract.get("httpStatus"), result.getResponse().getStatus());
        assertRequiredPaths(result.getResponse().getContentAsString(),
                castPathList(contract.get("requiredJsonPaths")));
    }

    private Map<String, Object> loadContract(String path) throws Exception {
        ClassPathResource resource = new ClassPathResource(path);
        try (InputStream inputStream = resource.getInputStream()) {
            return objectMapper.readValue(inputStream, new TypeReference<>() {
            });
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> castPathList(Object value) {
        return (List<String>) value;
    }

    private void assertRequiredPaths(String json, List<String> paths) {
        for (String path : paths) {
            try {
                JsonPath.read(json, path);
            } catch (Exception ex) {
                fail("missing path: " + path);
            }
        }
    }
}
