package com.aiot.rule.controller;

import com.aiot.common.config.GlobalResponseHandler;
import com.aiot.common.config.ResponseContractResolver;
import com.aiot.common.dto.ai.AiFeedbackResponse;
import com.aiot.common.exception.GlobalExceptionHandler;
import com.aiot.rule.service.AiDiagnosisService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AiDiagnosisControllerIntegrationTest {

    private MockMvc mockMvc;

    private AiDiagnosisService aiDiagnosisService;

    @BeforeEach
    void setUp() {
        aiDiagnosisService = mock(AiDiagnosisService.class);
        AiDiagnosisController controller = new AiDiagnosisController(aiDiagnosisService);
        ObjectMapper objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(
                        new GlobalExceptionHandler(),
                        new GlobalResponseHandler(objectMapper, new ResponseContractResolver())
                )
                .build();
    }

    @Test
    void feedbackShouldReturnWrappedDtoData() throws Exception {
        when(aiDiagnosisService.feedback(any())).thenReturn(AiFeedbackResponse.builder()
                .accepted(Boolean.TRUE)
                .diagnosisId("diag-1")
                .feedbackId("feedback-1")
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
                .andExpect(jsonPath("$.data.diagnosisId").value("diag-1"))
                .andExpect(jsonPath("$.data.feedbackId").value("feedback-1"))
                .andExpect(jsonPath("$.data.feedbackSaved").value(true));
    }
}
