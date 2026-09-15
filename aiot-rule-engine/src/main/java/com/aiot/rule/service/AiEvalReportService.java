package com.aiot.rule.service;

import com.aiot.rule.dto.AiEvalReportResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

@Service
public class AiEvalReportService {

    private final ObjectMapper objectMapper;
    private final String reportDir;

    public AiEvalReportService(ObjectMapper objectMapper,
                               @Value("${aiot.ai.eval.report-dir:training/data/reports}") String reportDir) {
        this.objectMapper = objectMapper;
        this.reportDir = reportDir;
    }

    public AiEvalReportResponse getRegressionGateReport(String sceneType) {
        String normalizedScene = normalizeScene(sceneType);
        Path reportPath = resolveReportPath(normalizedScene, "regression_gate");
        String normalizedReportPath = reportPath.toAbsolutePath().normalize().toString();
        if (!Files.exists(reportPath) || Files.isDirectory(reportPath)) {
            return AiEvalReportResponse.builder()
                    .sceneType(normalizedScene)
                    .reportType("regression_gate")
                    .reportPath(normalizedReportPath)
                    .exists(Boolean.FALSE)
                    .gatePassed(null)
                    .generatedAt(null)
                    .content(Map.of())
                    .build();
        }
        try {
            String payload = Files.readString(reportPath);
            Map<String, Object> content = objectMapper.readValue(payload, new TypeReference<Map<String, Object>>() {
            });
            Map<String, Object> safeContent = content == null ? Map.of() : content;
            return AiEvalReportResponse.builder()
                    .sceneType(normalizedScene)
                    .reportType("regression_gate")
                    .reportPath(normalizedReportPath)
                    .exists(Boolean.TRUE)
                    .gatePassed(booleanValue(safeContent.get("gatePassed")))
                    .generatedAt(Files.getLastModifiedTime(reportPath).toMillis())
                    .content(safeContent)
                    .build();
        } catch (IOException ex) {
            return AiEvalReportResponse.builder()
                    .sceneType(normalizedScene)
                    .reportType("regression_gate")
                    .reportPath(normalizedReportPath)
                    .exists(Boolean.TRUE)
                    .gatePassed(null)
                    .generatedAt(null)
                    .content(Map.of("error", "failed_to_read_report", "message", ex.getMessage()))
                    .build();
        }
    }

    private Path resolveReportPath(String sceneType, String reportType) {
        return resolveExistingPath(reportDir).resolve(sceneType.toLowerCase(Locale.ROOT) + "_" + reportType + ".json");
    }

    private Path resolveExistingPath(String configuredPath) {
        Path path = Path.of(configuredPath);
        if (path.isAbsolute()) {
            return path;
        }
        Path current = Path.of(System.getProperty("user.dir"));
        for (int i = 0; i < 4; i++) {
            Path candidate = current.resolve(configuredPath);
            if (Files.exists(candidate)) {
                return candidate;
            }
            if (current.getParent() == null) {
                break;
            }
            current = current.getParent();
        }
        return Path.of(System.getProperty("user.dir")).resolve(configuredPath);
    }

    private String normalizeScene(String sceneType) {
        if (!StringUtils.hasText(sceneType)) {
            return "OFFLINE_FLAP";
        }
        return sceneType.trim().toUpperCase(Locale.ROOT);
    }

    private Boolean booleanValue(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value == null) {
            return null;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }
}
