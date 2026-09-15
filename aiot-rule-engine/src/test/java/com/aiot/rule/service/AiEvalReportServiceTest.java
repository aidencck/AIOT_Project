package com.aiot.rule.service;

import com.aiot.rule.dto.AiEvalReportResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiEvalReportServiceTest {

    @Test
    void shouldReadRegressionGateReportFromConfiguredDirectory() throws Exception {
        Path reportsDir = Files.createTempDirectory("ai-eval-reports");
        Files.writeString(
                reportsDir.resolve("offline_flap_regression_gate.json"),
                "{\"scene\":\"OFFLINE_FLAP\",\"gatePassed\":true}"
        );
        AiEvalReportService service = new AiEvalReportService(new ObjectMapper(), reportsDir.toString());

        AiEvalReportResponse response = service.getRegressionGateReport("OFFLINE_FLAP");

        assertTrue(Boolean.TRUE.equals(response.getExists()));
        assertEquals("OFFLINE_FLAP", response.getSceneType());
        assertEquals(Boolean.TRUE, response.getGatePassed());
        assertEquals(Boolean.TRUE, response.getContent().get("gatePassed"));
    }

    @Test
    void shouldReturnMissingWhenReportFileDoesNotExist() throws Exception {
        Path reportsDir = Files.createTempDirectory("ai-eval-reports-empty");
        AiEvalReportService service = new AiEvalReportService(new ObjectMapper(), reportsDir.toString());

        AiEvalReportResponse response = service.getRegressionGateReport("SHADOW_DIFF");

        assertFalse(Boolean.TRUE.equals(response.getExists()));
        assertTrue(response.getContent().isEmpty());
    }
}
