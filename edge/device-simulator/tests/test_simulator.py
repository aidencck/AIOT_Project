"""device-simulator 事件流格式与幂等 eventId 测试。"""

from __future__ import annotations

import json
from datetime import datetime, timezone

from device_simulator.simulator import (
    EVENT_OFFLINE,
    EVENT_ONLINE,
    emit_jsonl,
    generate_device_profile,
    generate_events,
)

REQUIRED_PROFILE_KEYS = {"productKey", "deviceSn", "homeId", "firmwareVersion"}
REQUIRED_EVENT_KEYS = {
    "eventId",
    "sceneType",
    "eventType",
    "productKey",
    "deviceSn",
    "homeId",
    "firmwareVersion",
    "occurredAt",
    "seq",
}

START_AT = "2026-08-25T00:00:00Z"


def _iso_parseable(value: str) -> bool:
    text = value
    if text.endswith("Z"):
        text = text[:-1] + "+00:00"
    try:
        datetime.fromisoformat(text)
        return True
    except ValueError:
        return False


def test_profile_has_required_fields():
    profile = generate_device_profile(seed=42)
    assert set(profile.keys()) == REQUIRED_PROFILE_KEYS
    assert all(isinstance(v, str) and v for v in profile.values())


def test_profile_is_deterministic_for_same_seed():
    assert generate_device_profile(seed=7) == generate_device_profile(seed=7)


def test_event_stream_format():
    events = generate_events(
        generate_device_profile(seed=1), scene="OFFLINE_FLAP", count=5, start_at=START_AT
    )
    assert len(events) == 5
    for i, event in enumerate(events):
        assert set(event.keys()) == REQUIRED_EVENT_KEYS
        assert event["sceneType"] == "OFFLINE_FLAP"
        assert event["eventType"] in (EVENT_ONLINE, EVENT_OFFLINE)
        assert isinstance(event["eventId"], str) and event["eventId"]
        assert _iso_parseable(event["occurredAt"])
        assert event["seq"] == i


def test_offline_flap_alternates_online_offline():
    events = generate_events(
        generate_device_profile(seed=2), scene="OFFLINE_FLAP", count=6, start_at=START_AT
    )
    event_types = [e["eventType"] for e in events]
    assert event_types == [
        EVENT_ONLINE,
        EVENT_OFFLINE,
        EVENT_ONLINE,
        EVENT_OFFLINE,
        EVENT_ONLINE,
        EVENT_OFFLINE,
    ]


def test_event_id_is_idempotent():
    profile = generate_device_profile(seed=3)
    first = generate_events(profile, scene="OFFLINE_FLAP", count=10, start_at=START_AT)
    second = generate_events(profile, scene="OFFLINE_FLAP", count=10, start_at=START_AT)
    assert [e["eventId"] for e in first] == [e["eventId"] for e in second]


def test_event_ids_are_unique_within_stream():
    events = generate_events(
        generate_device_profile(seed=4), scene="OFFLINE_FLAP", count=20, start_at=START_AT
    )
    ids = [e["eventId"] for e in events]
    assert len(ids) == len(set(ids))


def test_jsonl_output_roundtrip(tmp_path):
    events = generate_events(
        generate_device_profile(seed=5), scene="OFFLINE_FLAP", count=4, start_at=START_AT
    )
    out = tmp_path / "events.jsonl"
    emit_jsonl(events, out)
    lines = out.read_text(encoding="utf-8").strip().splitlines()
    parsed = [json.loads(line) for line in lines]
    assert parsed == events
