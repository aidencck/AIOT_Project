package com.aiot.common.ai.schema;

import com.aiot.common.dto.ai.AiDiagnosisResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 诊断 JSON 校验器。
 *
 * <p>小参数量模型（如 qwen2.5:1.5b）常输出与强类型 schema 不一致的字段：
 * confidence 为中文等级（"高"/"中"/"低"）、evidence 为对象数组、riskLevel 为中文等。
 * 本类在反序列化前做宽松归一化，将上述变体收敛到 {@link AiDiagnosisResponse} 的强类型结构，
 * 避免 100% 回退到确定性兜底。</p>
 */
@Component
public class AiSchemaValidator {

    private static final Set<String> KNOWN_SCENE_TYPES = Set.of(
            "OFFLINE_FLAP", "PROVISION_FAILURE", "SHADOW_DIFF");

    /**
     * 提示词模板占位/示例标记。小模型常把模板里的字段示例原样输出（如
     * "场景枚举，如 OFFLINE_FLAP"、"根因类别英文短词"、"历史案例1"），
     * 命中即视为未真正推理，需拒绝以免把占位内容持久化为 source=llm。
     */
    private static final List<String> PLACEHOLDER_MARKERS = List.of(
            "场景枚举", "根因类别", "英文短词", "历史案例", "证据字符串",
            "建议字符串", "处理建议字符串", "一句话", "占位", "示例", "如 OFFLINE");

    private final ObjectMapper objectMapper;

    public AiSchemaValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AiDiagnosisResponse validateDiagnosisJson(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(stripMarkdownFences(json));
            if (root == null || !root.isObject()) {
                return null;
            }

            String sceneType = normalizeSceneType(textNode(root, "sceneType"));
            String summary = textNode(root, "summary");
            if (isPlaceholder(summary)) {
                summary = null;
            }
            String rootCauseCategory = normalizeRootCause(textNode(root, "rootCauseCategory"));
            if (isPlaceholder(rootCauseCategory)) {
                rootCauseCategory = null;
            }
            Double confidence = parseConfidence(root.get("confidence"));
            List<String> evidence = filterPlaceholders(toStringList(root.get("evidence")));
            List<String> recommendedActions = filterPlaceholders(toStringList(root.get("recommendedActions")));
            Boolean ruleDraftable = parseBoolean(root.get("ruleDraftable"));
            String riskLevel = normalizeRiskLevel(textNode(root, "riskLevel"));

            if (!StringUtils.hasText(sceneType)
                    || !StringUtils.hasText(summary)
                    || !StringUtils.hasText(rootCauseCategory)
                    || confidence == null
                    || ruleDraftable == null
                    || !StringUtils.hasText(riskLevel)
                    || evidence.isEmpty()
                    || recommendedActions.isEmpty()) {
                return null;
            }

            return AiDiagnosisResponse.builder()
                    .sceneType(sceneType)
                    .summary(summary)
                    .rootCauseCategory(rootCauseCategory)
                    .confidence(confidence)
                    .evidence(evidence)
                    .recommendedActions(recommendedActions)
                    .ruleDraftable(ruleDraftable)
                    .riskLevel(riskLevel)
                    .build();
        } catch (Exception ex) {
            return null;
        }
    }

    private String stripMarkdownFences(String json) {
        String trimmed = json.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline > 0) {
                trimmed = trimmed.substring(firstNewline + 1);
            }
            int lastFence = trimmed.lastIndexOf("```");
            if (lastFence >= 0) {
                trimmed = trimmed.substring(0, lastFence);
            }
        }
        return trimmed.trim();
    }

    private String textNode(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isNumber() || node.isBoolean()) {
            return node.asText();
        }
        return null;
    }

    private String normalizeSceneType(String raw) {
        if (!StringUtils.hasText(raw) || isPlaceholder(raw)) {
            return null;
        }
        String upper = raw.trim().toUpperCase(Locale.ROOT);
        if (KNOWN_SCENE_TYPES.contains(upper)) {
            return upper;
        }
        String low = raw.trim().toLowerCase(Locale.ROOT);
        if (low.contains("离线") || low.contains("抖动")) {
            return "OFFLINE_FLAP";
        }
        if (low.contains("配网")) {
            return "PROVISION_FAILURE";
        }
        if (low.contains("影子") || low.contains("漂移")) {
            return "SHADOW_DIFF";
        }
        return null;
    }

    private boolean isPlaceholder(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        for (String marker : PLACEHOLDER_MARKERS) {
            if (value.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private List<String> filterPlaceholders(List<String> values) {
        List<String> filtered = new ArrayList<>();
        for (String value : values) {
            if (!isPlaceholder(value)) {
                filtered.add(value);
            }
        }
        return filtered;
    }

    private Double parseConfidence(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.asDouble();
        }
        if (node.isTextual()) {
            String value = node.asText().trim();
            try {
                return Double.parseDouble(value);
            } catch (NumberFormatException ignored) {
                // 继续走中文/英文等级映射
            }
            String low = value.toLowerCase(Locale.ROOT);
            if (low.contains("高") || low.equals("high") || low.equals("critical") || low.contains("严重")) {
                return 0.9D;
            }
            if (low.contains("低") || low.equals("low")) {
                return 0.3D;
            }
            if (low.contains("中") || low.equals("medium")) {
                return 0.6D;
            }
        }
        return null;
    }

    private List<String> toStringList(JsonNode node) {
        List<String> result = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return result;
        }
        Iterator<JsonNode> elements = node.elements();
        while (elements.hasNext()) {
            JsonNode item = elements.next();
            if (item == null || item.isNull()) {
                continue;
            }
            if (item.isTextual()) {
                String value = item.asText().trim();
                if (!value.isEmpty()) {
                    result.add(value);
                }
            } else if (item.isObject()) {
                String value = firstText(item, "value", "text", "content", "description");
                String type = firstText(item, "type", "key", "name");
                if (StringUtils.hasText(value)) {
                    result.add(StringUtils.hasText(type) ? type + ": " + value : value);
                } else if (StringUtils.hasText(type)) {
                    result.add(type);
                } else {
                    result.add(item.toString());
                }
            } else if (item.isNumber() || item.isBoolean()) {
                result.add(item.asText());
            }
        }
        return result;
    }

    private String firstText(JsonNode object, String... fields) {
        for (String field : fields) {
            JsonNode node = object.get(field);
            if (node != null && node.isTextual() && StringUtils.hasText(node.asText())) {
                return node.asText().trim();
            }
        }
        return null;
    }

    private Boolean parseBoolean(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isNumber()) {
            return node.asInt() != 0;
        }
        if (node.isTextual()) {
            String value = node.asText().trim().toLowerCase(Locale.ROOT);
            if ("true".equals(value) || "是".equals(value) || "yes".equals(value) || "1".equals(value)
                    || "需要".equals(value) || "建议".equals(value) || "推荐".equals(value)) {
                return Boolean.TRUE;
            }
            if ("false".equals(value) || "否".equals(value) || "no".equals(value) || "0".equals(value)
                    || "不需要".equals(value)) {
                return Boolean.FALSE;
            }
        }
        return null;
    }

    private String normalizeRootCause(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String value = raw.trim();
        String low = value.toLowerCase(Locale.ROOT);

        if (low.contains("network") || low.contains("网络") || low.contains("抖动")
                || low.contains("离线") || low.contains("连接")) {
            return "NETWORK_INSTABILITY";
        }
        if (low.contains("performance") || low.contains("性能") || low.contains("卡顿")
                || low.contains("延迟")) {
            return "PERFORMANCE_DEGRADATION";
        }
        if (low.contains("configuration") || low.contains("配置") || low.contains("参数")
                || low.contains("错误")) {
            return "CONFIGURATION_ERROR";
        }
        if (low.contains("desired") || low.contains("reported") || low.contains("影子")
                || low.contains("漂移") || low.contains("差异") || low.contains("状态")
                || low.contains("state_drift") || low.contains("state_sync_drift")
                || low.contains("state drift")) {
            return "STATE_SYNC_DRIFT";
        }
        if (low.contains("provisioning") || low.contains("provision") || low.contains("配网")
                || low.contains("绑定") || low.contains("token")) {
            return "PROVISIONING_CONFLICT";
        }
        return value;
    }

    private String normalizeRiskLevel(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        String low = trimmed.toLowerCase(Locale.ROOT);
        if (low.contains("高") || low.equals("high") || low.equals("critical")
                || low.contains("严重") || low.contains("紧急")) {
            return "HIGH";
        }
        if (low.contains("低") || low.equals("low")) {
            return "LOW";
        }
        if (low.contains("中") || low.equals("medium")) {
            return "MEDIUM";
        }
        String upper = trimmed.toUpperCase(Locale.ROOT);
        if ("HIGH".equals(upper) || "MEDIUM".equals(upper) || "LOW".equals(upper)) {
            return upper;
        }
        return trimmed;
    }
}
