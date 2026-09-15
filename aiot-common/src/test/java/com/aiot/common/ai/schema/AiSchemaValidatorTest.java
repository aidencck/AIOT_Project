package com.aiot.common.ai.schema;

import com.aiot.common.dto.ai.AiDiagnosisResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiSchemaValidatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AiSchemaValidator aiSchemaValidator = new AiSchemaValidator(objectMapper);

    @Test
    void shouldParseValidDiagnosisJson() throws Exception {
        String json = objectMapper.writeValueAsString(AiDiagnosisResponse.builder()
                .sceneType("OFFLINE_FLAP")
                .summary("设备离线")
                .rootCauseCategory("NETWORK_INSTABILITY")
                .confidence(0.9D)
                .evidence(List.of("e1"))
                .recommendedActions(List.of("a1"))
                .ruleDraftable(Boolean.TRUE)
                .riskLevel("HIGH")
                .build());

        AiDiagnosisResponse response = aiSchemaValidator.validateDiagnosisJson(json);

        assertNotNull(response);
        assertEquals("OFFLINE_FLAP", response.getSceneType());
    }

    @Test
    void shouldRejectInvalidDiagnosisJson() {
        String json = "{\"sceneType\":\"OFFLINE_FLAP\"}";

        AiDiagnosisResponse response = aiSchemaValidator.validateDiagnosisJson(json);

        assertNull(response);
    }

    @Test
    void shouldNormalizeChineseConfidenceAndObjectEvidence() {
        // 复现 qwen2.5:1.5b 真实输出：confidence 中文、evidence 对象数组、riskLevel 中文
        String json = "{"
                + "\"sceneType\":\"offline_flap\","
                + "\"summary\":\"设备频繁离线\","
                + "\"rootCauseCategory\":\"设备故障\","
                + "\"confidence\":\"高\","
                + "\"evidence\":[{\"type\":\"设备状态\",\"value\":\"频繁上下线\"}],"
                + "\"recommendedActions\":[\"检查供电\",\"检查网络覆盖\"],"
                + "\"ruleDraftable\":\"是\","
                + "\"riskLevel\":\"中等\""
                + "}";

        AiDiagnosisResponse response = aiSchemaValidator.validateDiagnosisJson(json);

        assertNotNull(response);
        assertEquals("OFFLINE_FLAP", response.getSceneType());
        assertEquals(0.9D, response.getConfidence());
        assertEquals("MEDIUM", response.getRiskLevel());
        assertTrue(Boolean.TRUE.equals(response.getRuleDraftable()));
        assertEquals(1, response.getEvidence().size());
        assertTrue(response.getEvidence().get(0).contains("频繁上下线"));
        assertEquals(2, response.getRecommendedActions().size());
    }

    @Test
    void shouldNormalizeNumericStringConfidenceAndStringBoolean() {
        String json = "{"
                + "\"sceneType\":\"SHADOW_DIFF\","
                + "\"summary\":\"影子漂移\","
                + "\"rootCauseCategory\":\"STATE_SYNC_DRIFT\","
                + "\"confidence\":\"0.75\","
                + "\"evidence\":[\"desired 与 reported 不一致\"],"
                + "\"recommendedActions\":[\"检查 ACK 链路\"],"
                + "\"ruleDraftable\":\"true\","
                + "\"riskLevel\":\"LOW\""
                + "}";

        AiDiagnosisResponse response = aiSchemaValidator.validateDiagnosisJson(json);

        assertNotNull(response);
        assertEquals(0.75D, response.getConfidence());
        assertTrue(Boolean.TRUE.equals(response.getRuleDraftable()));
        assertEquals("LOW", response.getRiskLevel());
    }

    @Test
    void shouldStripMarkdownFences() {
        String json = "```json\n"
                + "{\"sceneType\":\"OFFLINE_FLAP\",\"summary\":\"s\",\"rootCauseCategory\":\"c\","
                + "\"confidence\":0.8,\"evidence\":[\"e\"],\"recommendedActions\":[\"a\"],"
                + "\"ruleDraftable\":true,\"riskLevel\":\"MEDIUM\"}"
                + "\n```";

        AiDiagnosisResponse response = aiSchemaValidator.validateDiagnosisJson(json);

        assertNotNull(response);
        assertEquals("MEDIUM", response.getRiskLevel());
    }

    @Test
    void shouldNormalizeRootCauseCategory() {
        // 实测中英混用取值
        assertRootCauseNormalized("性能", "PERFORMANCE_DEGRADATION");
        assertRootCauseNormalized("状态漂移", "STATE_SYNC_DRIFT");
        assertRootCauseNormalized("CONFIGURATION", "CONFIGURATION_ERROR");
        assertRootCauseNormalized("NETWORK", "NETWORK_INSTABILITY");
        assertRootCauseNormalized("配置错误", "CONFIGURATION_ERROR");
        assertRootCauseNormalized("状态差异", "STATE_SYNC_DRIFT");

        // 已标准化的英文取值保持稳定
        assertRootCauseNormalized("NETWORK_INSTABILITY", "NETWORK_INSTABILITY");
        assertRootCauseNormalized("STATE_SYNC_DRIFT", "STATE_SYNC_DRIFT");
        assertRootCauseNormalized("PROVISIONING_CONFLICT", "PROVISIONING_CONFLICT");
        assertRootCauseNormalized("STATE_DRIFT", "STATE_SYNC_DRIFT");
        assertRootCauseNormalized("PROVISION_FAILURE", "PROVISIONING_CONFLICT");
        assertRootCauseNormalized("PROVISION", "PROVISIONING_CONFLICT");

        // 其他英文/中文同义取值
        assertRootCauseNormalized("network", "NETWORK_INSTABILITY");
        assertRootCauseNormalized("configuration error", "CONFIGURATION_ERROR");
        assertRootCauseNormalized("state drift", "STATE_SYNC_DRIFT");
        assertRootCauseNormalized("provision failure", "PROVISIONING_CONFLICT");
        assertRootCauseNormalized("配网失败", "PROVISIONING_CONFLICT");
        assertRootCauseNormalized("token 失效", "PROVISIONING_CONFLICT");

        // 无法匹配时保持原值
        assertRootCauseNormalized("未知类别", "未知类别");
    }

    @Test
    void shouldRejectTemplatePlaceholderLeak() {
        // 复现 qwen2.5:1.5b 把提示词模板占位原样输出的真实泄漏
        String json = "{"
                + "\"sceneType\":\"场景枚举，如 OFFLINE_FLAP\","
                + "\"summary\":\"一句话诊断摘要\","
                + "\"rootCauseCategory\":\"根因类别英文短词\","
                + "\"confidence\":0.8,"
                + "\"evidence\":[\"历史案例1\",\"历史案例2\",\"历史案例3\"],"
                + "\"recommendedActions\":[\"处理建议字符串1\"],"
                + "\"ruleDraftable\":true,"
                + "\"riskLevel\":\"HIGH\""
                + "}";

        assertNull(aiSchemaValidator.validateDiagnosisJson(json));
    }

    @Test
    void shouldRejectPlaceholderEvidenceEvenWithValidHeader() {
        // 场景/根因已正确，但 evidence 仍是模板占位，整体应拒绝
        String json = "{"
                + "\"sceneType\":\"OFFLINE_FLAP\","
                + "\"summary\":\"设备频繁离线\","
                + "\"rootCauseCategory\":\"NETWORK_INSTABILITY\","
                + "\"confidence\":0.8,"
                + "\"evidence\":[\"证据字符串1\",\"证据字符串2\"],"
                + "\"recommendedActions\":[\"检查供电\"],"
                + "\"ruleDraftable\":true,"
                + "\"riskLevel\":\"HIGH\""
                + "}";

        assertNull(aiSchemaValidator.validateDiagnosisJson(json));
    }

    private void assertRootCauseNormalized(String input, String expected) {
        String json = "{"
                + "\"sceneType\":\"OFFLINE_FLAP\","
                + "\"summary\":\"s\","
                + "\"rootCauseCategory\":\"" + input + "\","
                + "\"confidence\":0.8,"
                + "\"evidence\":[\"e\"],"
                + "\"recommendedActions\":[\"a\"],"
                + "\"ruleDraftable\":true,"
                + "\"riskLevel\":\"MEDIUM\""
                + "}";

        AiDiagnosisResponse response = aiSchemaValidator.validateDiagnosisJson(json);

        assertNotNull(response);
        assertEquals(expected, response.getRootCauseCategory());
    }
}
