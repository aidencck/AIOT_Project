package com.aiot.home.contract;

import com.aiot.common.config.GlobalResponseHandler;
import com.aiot.common.exception.GlobalExceptionHandler;
import com.aiot.home.dto.LoginResp;
import com.aiot.home.service.UserService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@WebMvcTest(controllers = com.aiot.home.controller.UserController.class)
@Import({GlobalResponseHandler.class, GlobalExceptionHandler.class})
class UserApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

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
            Object value = JsonPath.read(json, path);
            assertNotNull(value, "missing path: " + path);
        }
    }
}
