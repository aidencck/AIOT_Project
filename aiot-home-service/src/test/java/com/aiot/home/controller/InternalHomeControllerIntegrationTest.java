package com.aiot.home.controller;

import com.aiot.common.exception.GlobalExceptionHandler;
import com.aiot.home.config.AuthInterceptor;
import com.aiot.home.service.HomeCacheManager;
import com.aiot.home.service.HomeService;
import com.aiot.home.service.RoomService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InternalHomeControllerIntegrationTest {

    private MockMvc mockMvc;
    private HomeCacheManager homeCacheManager;
    private HomeService homeService;
    private RoomService roomService;

    @BeforeEach
    void setUp() {
        homeCacheManager = mock(HomeCacheManager.class);
        homeService = mock(HomeService.class);
        roomService = mock(RoomService.class);
        InternalHomeController controller = new InternalHomeController();
        ReflectionTestUtils.setField(controller, "homeCacheManager", homeCacheManager);
        ReflectionTestUtils.setField(controller, "homeService", homeService);
        ReflectionTestUtils.setField(controller, "roomService", roomService);

        AuthInterceptor authInterceptor = new AuthInterceptor();
        ReflectionTestUtils.setField(authInterceptor, "internalToken", "inner-secret-token-2026-strong");

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .addInterceptors(authInterceptor)
                .build();
    }

    @Test
    void checkPermission_shouldReturnTrue_whenInternalTokenAndUserHeadersValid() throws Exception {
        when(homeCacheManager.getUserRole("h-1", "u-1")).thenReturn(2);

        mockMvc.perform(get("/api/v1/internal/homes/h-1/permission/check")
                        .param("minRole", "3")
                        .header("X-Internal-Token", "inner-secret-token-2026-strong")
                        .header("X-User-Id", "u-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    void checkPermission_shouldReject_whenMissingInternalToken() throws Exception {
        mockMvc.perform(get("/api/v1/internal/homes/h-1/permission/check")
                        .param("minRole", "3")
                        .header("X-User-Id", "u-1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void checkRelation_shouldReturnHomeAndRoomRelation() throws Exception {
        when(homeService.existsHome("h-1")).thenReturn(true);
        when(roomService.existsRoom("r-1")).thenReturn(true);
        when(roomService.roomBelongsToHome("r-1", "h-1")).thenReturn(true);

        mockMvc.perform(get("/api/v1/internal/homes/h-1/relation/check")
                        .param("roomId", "r-1")
                        .header("X-Internal-Token", "inner-secret-token-2026-strong")
                        .header("X-User-Id", "u-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.homeExists").value(true))
                .andExpect(jsonPath("$.data.roomExists").value(true))
                .andExpect(jsonPath("$.data.roomBelongsToHome").value(true));
    }
}
