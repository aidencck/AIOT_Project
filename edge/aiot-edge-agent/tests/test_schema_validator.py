"""diagnosis.schema.json 校验通过/失败用例测试。"""

from __future__ import annotations

from aiot_edge_agent.schema_validator import load_schema, validate

REQUIRED_FIELDS = [
    "sceneType",
    "summary",
    "rootCauseCategory",
    "confidence",
    "evidence",
    "recommendedActions",
    "ruleDraftable",
    "riskLevel",
]


def _valid():
    return {
        "sceneType": "OFFLINE_FLAP",
        "summary": "设备发生在线/离线翻转",
        "rootCauseCategory": "NETWORK_INSTABILITY",
        "confidence": 0.5,
        "evidence": ["短时间内多次翻转"],
        "recommendedActions": ["检查网络"],
        "ruleDraftable": True,
        "riskLevel": "LOW",
    }


def test_schema_file_declares_eight_required_fields():
    schema = load_schema()
    assert len(schema["required"]) == 8
    assert set(schema["required"]) == set(REQUIRED_FIELDS)


def test_valid_diagnosis_passes():
    ok, errors = validate(_valid())
    assert ok, errors


def test_missing_required_fields_fail():
    ok, errors = validate({})
    assert not ok
    joined = " ".join(errors)
    for field in REQUIRED_FIELDS:
        assert field in joined


def test_confidence_out_of_range_fails():
    doc = _valid()
    doc["confidence"] = 1.5
    ok, _ = validate(doc)
    assert not ok


def test_empty_evidence_fails():
    doc = _valid()
    doc["evidence"] = []
    ok, _ = validate(doc)
    assert not ok


def test_empty_recommended_actions_fails():
    doc = _valid()
    doc["recommendedActions"] = []
    ok, _ = validate(doc)
    assert not ok


def test_wrong_types_fail():
    doc = _valid()
    doc["ruleDraftable"] = "yes"
    ok, _ = validate(doc)
    assert not ok
