package com.aiot.home.controller;

import com.aiot.common.config.GlobalResponseHandler;
import com.aiot.common.config.ResponseContractResolver;
import com.aiot.common.exception.GlobalExceptionHandler;
import com.aiot.home.config.AuthInterceptor;
import com.aiot.home.config.WebMvcConfig;
import com.aiot.home.dto.LoginResp;
import com.aiot.home.service.UserService;
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

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UserController.class, properties = {
        "AIOT_JWT_SECRET=0123456789abcdef0123456789abcdef"
})
@ContextConfiguration(classes = UserControllerWebMvcTest.MvcSliceConfig.class)
class UserControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @Test
    void shouldRegisterWithoutGatewayHeaderBecausePublicPathIsExcluded() throws Exception {
        doNothing().when(userService).register(ArgumentMatchers.any());

        mockMvc.perform(post("/api/v1/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "phone": "13800138000",
                                  "password": "123456",
                                  "nickname": "alice"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(content().string(""));
    }

    @Test
    void shouldWrapLoginResponseThroughSpringMvcSlice() throws Exception {
        LoginResp resp = new LoginResp();
        resp.setToken("jwt-token");
        resp.setUserId("u-1");
        resp.setNickname("alice");
        when(userService.login(ArgumentMatchers.any())).thenReturn(resp);

        mockMvc.perform(post("/api/v1/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "phone": "13800138000",
                                  "password": "123456"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").value("jwt-token"));
    }

    @Test
    void shouldValidateRegisterPayloadInMvcLayer() throws Exception {
        mockMvc.perform(post("/api/v1/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "phone": "bad-phone",
                                  "password": "123",
                                  "nickname": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({
            UserController.class,
            WebMvcConfig.class,
            AuthInterceptor.class,
            GlobalExceptionHandler.class,
            GlobalResponseHandler.class,
            ResponseContractResolver.class
    })
    static class MvcSliceConfig {
    }
}
