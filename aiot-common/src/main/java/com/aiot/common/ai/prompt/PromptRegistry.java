package com.aiot.common.ai.prompt;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class PromptRegistry {

    private static final String SCHEMA_CONTRACT = "\n\n"
            + "请严格按以下字段与类型输出一个 JSON 对象（不要输出 markdown 代码块，不要添加任何解释文字）：\n"
            + "{\n"
            + "  \"sceneType\": \"OFFLINE_FLAP\",\n"
            + "  \"summary\": \"设备频繁离线\",\n"
            + "  \"rootCauseCategory\": \"NETWORK_INSTABILITY\",\n"
            + "  \"confidence\": 0.8,\n"
            + "  \"evidence\": [\"心跳超时\", \"网络不稳定\"],\n"
            + "  \"recommendedActions\": [\"检查供电\", \"检查网络\"],\n"
            + "  \"ruleDraftable\": true,\n"
            + "  \"riskLevel\": \"HIGH\"\n"
            + "}\n"
            + "硬性要求：\n"
            + "- sceneType 只能是 OFFLINE_FLAP、PROVISION_FAILURE、SHADOW_DIFF 之一；\n"
            + "- rootCauseCategory 只能是 NETWORK_INSTABILITY、PERFORMANCE_DEGRADATION、CONFIGURATION_ERROR、STATE_SYNC_DRIFT、PROVISIONING_CONFLICT 之一；\n"
            + "- confidence 必须是 0.0 到 1.0 之间的数字；\n"
            + "- evidence 与 recommendedActions 必须是字符串数组，内容必须来自设备上下文与历史案例，禁止填占位描述；\n"
            + "- ruleDraftable 必须是布尔值 true 或 false；\n"
            + "- riskLevel 只能是 HIGH、MEDIUM、LOW 之一。";

    private static final Map<String, String> SCENE_INSTRUCTIONS = Map.of(
            "OFFLINE_FLAP", "你是 AIoT 运维诊断助手。请基于设备上下文诊断设备高频离线/上下线抖动问题。",
            "PROVISION_FAILURE", "你是 AIoT 配网诊断助手。请基于配网事件和设备上下文诊断配网失败问题。",
            "SHADOW_DIFF", "你是 AIoT 状态对账助手。请基于设备影子 reported/desired 差异诊断状态漂移问题。"
    );

    public String getDiagnosisPrompt(String sceneType) {
        String scene = normalize(sceneType);
        String instruction = SCENE_INSTRUCTIONS.getOrDefault(
                scene,
                "你是 AIoT 诊断助手。请基于设备上下文输出结构化诊断。"
        );
        return instruction + SCHEMA_CONTRACT;
    }

    public String getDiagnosisPromptVersion(String sceneType) {
        return "diagnosis-" + normalize(sceneType) + "-v2";
    }

    private String normalize(String sceneType) {
        return sceneType == null ? "GENERIC" : sceneType.trim().toUpperCase();
    }
}
