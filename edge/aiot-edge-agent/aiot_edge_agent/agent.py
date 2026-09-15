"""端侧诊断代理：读取事件流 -> 组装上下文 -> 推理 -> schema 校验 -> 兜底。"""

from __future__ import annotations

import json
from typing import Any, Dict, List, Optional, Sequence, Tuple

from .llm_client import LlmClient
from .schema_validator import CONTRACTS_DIR, load_schema, validate

SCENE_TO_PROMPT_VERSION = {
    "OFFLINE_FLAP": "diagnosis-OFFLINE_FLAP-v1",
    "PROVISION_FAILURE": "diagnosis-PROVISION_FAILURE-v1",
    "SHADOW_DIFF": "diagnosis-SHADOW_DIFF-v1",
}

DEFAULT_SYSTEM_TEMPLATE = (
    "你是 AIoT 运维诊断助手。请基于设备上下文输出结构化诊断 JSON，"
    "必须包含 sceneType、summary、rootCauseCategory、confidence、evidence、"
    "recommendedActions、ruleDraftable、riskLevel。"
)


class EdgeAgent:
    def __init__(self, llm_client: Any = None, schema_path: Optional[Any] = None) -> None:
        self.llm = llm_client if llm_client is not None else LlmClient()
        self.schema = load_schema(schema_path)

    def diagnose(self, events: Sequence[Dict[str, Any]]) -> Dict[str, Any]:
        scene = self._detect_scene(events)
        context = self.build_context(events, scene)
        llm_result = self._try_llm(scene, context)
        if llm_result is not None:
            ok, _ = validate(llm_result, schema=self.schema)
            if ok:
                return llm_result
        return self._fallback(scene, context)

    def build_context(self, events: Sequence[Dict[str, Any]], scene: str) -> Dict[str, Any]:
        online = sum(1 for e in events if e.get("eventType") == "DEVICE_ONLINE")
        offline = sum(1 for e in events if e.get("eventType") == "DEVICE_OFFLINE")
        first = events[0] if events else {}
        return {
            "sceneType": scene,
            "deviceSn": first.get("deviceSn"),
            "productKey": first.get("productKey"),
            "homeId": first.get("homeId"),
            "firmwareVersion": first.get("firmwareVersion"),
            "eventCount": len(events),
            "onlineCount": online,
            "offlineCount": offline,
            "flapCount": min(online, offline) if scene == "OFFLINE_FLAP" else max(online, offline),
            "recentEvents": events[-5:],
        }

    def _detect_scene(self, events: Sequence[Dict[str, Any]]) -> str:
        for event in events:
            scene = event.get("sceneType")
            if scene:
                return scene
        return "OFFLINE_FLAP"

    def _try_llm(self, scene: str, context: Dict[str, Any]) -> Optional[Dict[str, Any]]:
        if not getattr(self.llm, "is_configured", True):
            return None
        template, _ = self._load_prompt(scene)
        messages = [
            {"role": "system", "content": template},
            {"role": "user", "content": json.dumps(context, ensure_ascii=False, indent=2)},
        ]
        try:
            return self.llm.complete_json(messages)
        except Exception:
            return None

    def _load_prompt(self, scene: str) -> Tuple[str, Optional[str]]:
        prompt_file = CONTRACTS_DIR / "prompts" / f"{scene}.prompt.json"
        template = DEFAULT_SYSTEM_TEMPLATE
        prompt_version: Optional[str] = SCENE_TO_PROMPT_VERSION.get(scene)
        if prompt_file.exists():
            try:
                data = json.loads(prompt_file.read_text(encoding="utf-8"))
                template = data.get("template", template)
                prompt_version = data.get("promptVersion", prompt_version)
            except (OSError, json.JSONDecodeError):
                pass
        return template, prompt_version

    def _fallback(self, scene: str, context: Dict[str, Any]) -> Dict[str, Any]:
        _, prompt_version = self._load_prompt(scene)
        device_sn = context.get("deviceSn") or "unknown-device"
        product_key = context.get("productKey") or "unknown-product"
        firmware = context.get("firmwareVersion") or "unknown"
        flap = context.get("flapCount", 0)

        if scene == "OFFLINE_FLAP":
            summary = f"设备 {device_sn} 在观测窗口内发生 {flap} 次在线/离线翻转，判定为网络或供电不稳定。"
            root_cause = "NETWORK_INSTABILITY"
            confidence = round(min(0.5 + 0.05 * flap, 0.95), 2)
            risk = "MEDIUM" if flap >= 3 else "LOW"
            actions = [
                "检查设备 Wi-Fi 信号强度与网关在线状态",
                "核对设备供电与掉电日志",
                "若持续翻转则升级为现场检修工单",
            ]
            rule_draftable = flap >= 2
        elif scene == "PROVISION_FAILURE":
            summary = f"设备 {device_sn} 配网失败，疑似入网凭证或鉴权链路异常。"
            root_cause = "PROVISION_CREDENTIAL_ERROR"
            confidence = 0.9
            risk = "HIGH"
            actions = [
                "核对 productKey/deviceSn 与预置凭证",
                "检查 ProvisionController 与 EmqxAuthController 鉴权结果",
                "重置设备并重新走配网流程",
            ]
            rule_draftable = True
        else:  # SHADOW_DIFF
            summary = f"设备 {device_sn} 影子 reported/desired 出现偏差，需状态对账。"
            root_cause = "SHADOW_STATE_DIVERGENCE"
            confidence = 0.8
            risk = "LOW"
            actions = [
                "下发 desired 状态对齐指令",
                "核对物模型字段映射",
                "观察下一次 reported 上报是否收敛",
            ]
            rule_draftable = True

        evidence = [
            f"事件流共 {context.get('eventCount', 0)} 条，翻转 {flap} 次",
            f"设备 productKey={product_key}，固件版本 {firmware}",
            f"场景 {scene} 确定性规则链兜底",
        ]
        return {
            "diagnosisId": f"diag-{scene}-{device_sn}",
            "sceneType": scene,
            "summary": summary,
            "rootCauseCategory": root_cause,
            "confidence": confidence,
            "evidence": evidence,
            "recommendedActions": actions,
            "ruleDraftable": rule_draftable,
            "riskLevel": risk,
            "modelName": "deterministic-fallback",
            "promptVersion": prompt_version,
            "source": "edge-agent",
        }
