package com.aiot.rule.controller;

import com.aiot.common.config.GlobalResponseHandler;
import com.aiot.common.config.ResponseContractResolver;
import com.aiot.common.dto.ai.AiFeedbackResponse;
import com.aiot.common.exception.GlobalExceptionHandler;
import com.aiot.rule.service.AiDiagnosisService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AiDiagnosisController.class, properties = {
        "AIOT_JWT_SECRET=0123456789abcdef0123456789abcdef"
})
@ContextConfiguration(classes = AiDiagnosisControllerWebMvcTest.MvcSliceConfig.class)
class AiDiagnosisControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AiDiagnosisService aiDiagnosisService;

    @Test
    void shouldWrapFeedbackResponseThroughSpringMvcSlice() throws Exception {
        when(aiDiagnosisService.feedback(ArgumentMatchers.any())).thenReturn(AiFeedbackResponse.builder()
                .accepted(Boolean.TRUE)
                .diagnosisId("diag-1")
                .feedbackId("fb-1")
                .feedbackSaved(Boolean.TRUE)
                .build());

        mockMvc.perform(post("/api/v1/ai/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "diagnosisId": "diag-1",
                                  "feedbackType": "ACCEPTED",
                                  "resolutionStatus": "SOLVED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.feedbackId").value("fb-1"));
    }

    @Test
    void shouldValidateFeedbackPayloadInMvcLayer() throws Exception {
        mockMvc.perform(post("/api/v1/ai/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "feedbackType": "ACCEPTED",
                                  "resolutionStatus": "SOLVED"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void shouldValidateSearchLimitRange() throws Exception {
        mockMvc.perform(get("/api/v1/ai/cases/search")
                        .param("sceneType", "OFFLINE_FLAP")
                        .param("limit", "21"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({
            AiDiagnosisController.class,
            GlobalExceptionHandler.class,
            GlobalResponseHandler.class,
            ResponseContractResolver.class
    })
    static class MvcSliceConfig {
    }
}
