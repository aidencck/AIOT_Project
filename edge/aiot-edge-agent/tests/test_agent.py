"""edge-agent 诊断流水线与 schema 校验兜底测试。"""

from __future__ import annotations

from aiot_edge_agent.agent import EdgeAgent
from aiot_edge_agent.llm_client import LlmClient, MockLlmClient
from aiot_edge_agent.schema_validator import validate

REQUIRED_FIELDS = {
    "sceneType",
    "summary",
    "rootCauseCategory",
    "confidence",
    "evidence",
    "recommendedActions",
    "ruleDraftable",
    "riskLevel",
}


def _events(count: int = 6):
    return [
        {
            "eventId": f"evt-{i:06d}",
            "sceneType": "OFFLINE_FLAP",
            "eventType": "DEVICE_ONLINE" if i % 2 == 0 else "DEVICE_OFFLINE",
            "productKey": "a1SmartDesk",
            "deviceSn": "a1SmartDesk123456",
            "homeId": "home-12345",
            "firmwareVersion": "1.1.2",
            "occurredAt": f"2026-08-25T00:{i:02d}:00Z",
            "seq": i,
        }
        for i in range(count)
    ]


def _valid_llm_result():
    return {
        "sceneType": "OFFLINE_FLAP",
        "summary": "模型输出摘要",
        "rootCauseCategory": "NETWORK_INSTABILITY",
        "confidence": 0.85,
        "evidence": ["短时间窗内多次翻转"],
        "recommendedActions": ["检查网络"],
        "ruleDraftable": True,
        "riskLevel": "MEDIUM",
        "modelName": "mock-model",
    }


def _assert_schema_valid(result):
    assert REQUIRED_FIELDS.issubset(set(result.keys()))
    ok, errors = validate(result, schema=EdgeAgent().schema)
    assert ok, errors


def test_unconfigured_llm_uses_fallback():
    agent = EdgeAgent(llm_client=LlmClient())  # 未配置 base_url/api_key/model
    result = agent.diagnose(_events())
    _assert_schema_valid(result)
    assert result["modelName"] == "deterministic-fallback"


def test_schema_validation_failure_triggers_fallback():
    invalid = _valid_llm_result()
    invalid.pop("summary")  # 缺失必填字段
    invalid["confidence"] = 1.5  # 超出 [0,1] 范围
    agent = EdgeAgent(llm_client=MockLlmClient(result=invalid))
    result = agent.diagnose(_events())
    _assert_schema_valid(result)
    assert result["modelName"] == "deterministic-fallback"
    assert result["confidence"] <= 1.0


def test_valid_llm_output_passes_through():
    valid = _valid_llm_result()
    agent = EdgeAgent(llm_client=MockLlmClient(result=valid))
    result = agent.diagnose(_events())
    assert result == valid


def test_llm_raising_exception_falls_back():
    agent = EdgeAgent(llm_client=MockLlmClient(raises=RuntimeError("boom")))
    result = agent.diagnose(_events())
    _assert_schema_valid(result)
    assert result["modelName"] == "deterministic-fallback"


def test_llm_non_dict_result_falls_back():
    agent = EdgeAgent(llm_client=MockLlmClient(result=None))
    result = agent.diagnose(_events())
    _assert_schema_valid(result)
    assert result["modelName"] == "deterministic-fallback"
