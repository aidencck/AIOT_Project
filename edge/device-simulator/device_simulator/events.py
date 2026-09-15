"""事件流生成：7 类设备事件、场景事件序列与完整生命周期序列。"""

from __future__ import annotations

import hashlib
import random
from datetime import datetime, timedelta, timezone
from typing import Any, Dict, List, Optional, Sequence

from .shadow import _generate_shadow_state

# 7 类设备事件类型（对齐 DeviceEventType）
EVENT_DEVICE_ONLINE = "DEVICE_ONLINE"
EVENT_DEVICE_OFFLINE = "DEVICE_OFFLINE"
EVENT_PROVISION_SUCCEEDED = "DEVICE_PROVISION_SUCCEEDED"
EVENT_PROVISION_REJECTED = "DEVICE_PROVISION_REJECTED"
EVENT_PROVISION_FAILED = "DEVICE_PROVISION_FAILED"
EVENT_SHADOW_DESIRED_UPDATED = "SHADOW_DESIRED_UPDATED"
EVENT_SHADOW_REPORTED_UPDATED = "SHADOW_REPORTED_UPDATED"

ALL_EVENT_TYPES: Sequence[str] = (
    EVENT_DEVICE_ONLINE,
    EVENT_DEVICE_OFFLINE,
    EVENT_PROVISION_SUCCEEDED,
    EVENT_PROVISION_REJECTED,
    EVENT_PROVISION_FAILED,
    EVENT_SHADOW_DESIRED_UPDATED,
    EVENT_SHADOW_REPORTED_UPDATED,
)

SCENES: Sequence[str] = ("OFFLINE_FLAP", "PROVISION_FAILURE", "SHADOW_DIFF")

# 完整生命周期阶段：配网→激活→上线→遥测→影子→OTA→掉线→重连
LIFECYCLE_STAGES: Sequence[str] = (
    "provision",
    "activate",
    "online",
    "telemetry",
    "shadow",
    "ota",
    "offline",
    "reconnect",
)


def event_id(device_id: str, timestamp: str, event_type: str) -> str:
    """确定性幂等 eventId：同一设备同一时刻同一事件类型生成相同 id。"""
    digest = hashlib.sha1(
        f"{device_id}|{timestamp}|{event_type}".encode("utf-8")
    ).hexdigest()
    return f"evt-{digest[:24]}"


def _parse_time(value: Any) -> Optional[datetime]:
    if value is None:
        return None
    if isinstance(value, datetime):
        dt = value
    else:
        text = str(value)
        if text.endswith("Z"):
            text = text[:-1] + "+00:00"
        dt = datetime.fromisoformat(text)
    if dt.tzinfo is None:
        dt = dt.replace(tzinfo=timezone.utc)
    return dt.astimezone(timezone.utc)


def _iso_utc(dt: datetime) -> str:
    return dt.astimezone(timezone.utc).isoformat().replace("+00:00", "Z")


def _trace_id(device_id: str, timestamp: str, seq: int) -> str:
    digest = hashlib.sha1(
        f"{device_id}|{timestamp}|{seq}".encode("utf-8")
    ).hexdigest()
    return f"trace-{digest[:16]}"


def _make_event(
    profile: Dict[str, Any],
    event_type: str,
    timestamp: str,
    seq: int,
    version: int,
    payload: Dict[str, Any],
    scene: str,
) -> Dict[str, Any]:
    device_id = profile["globalDeviceId"]
    return {
        "eventId": event_id(device_id, timestamp, event_type),
        "eventType": event_type,
        "deviceId": device_id,
        "sceneType": scene,
        "timestamp": timestamp,
        "source": "device",
        "traceId": _trace_id(device_id, timestamp, seq),
        "version": version,
        "payload": payload,
        "seq": seq,
        # 上下文字段
        "productKey": profile["productKey"],
        "deviceSn": profile["deviceSn"],
        "homeId": profile["homeId"],
        "firmwareVersion": profile["firmwareVersion"],
    }


def _shadow_payload(rng: random.Random, version: int) -> Dict[str, Any]:
    state = _generate_shadow_state(rng)
    state["version"] = version
    return state


def _scene_event_types(scene: str, count: int) -> List[str]:
    if scene == "OFFLINE_FLAP":
        return [EVENT_DEVICE_ONLINE if i % 2 == 0 else EVENT_DEVICE_OFFLINE for i in range(count)]
    if scene == "PROVISION_FAILURE":
        return [EVENT_PROVISION_FAILED] * count
    if scene == "SHADOW_DIFF":
        return [
            EVENT_SHADOW_REPORTED_UPDATED if i % 2 == 0 else EVENT_SHADOW_DESIRED_UPDATED
            for i in range(count)
        ]
    raise ValueError(f"unsupported scene: {scene}")


def _payload_for(
    event_type: str, scene: str, rng: random.Random, version: int, seq: int
) -> Dict[str, Any]:
    if scene == "SHADOW_DIFF":
        return _shadow_payload(rng, version)
    if event_type == EVENT_DEVICE_ONLINE:
        return {"status": 1}
    if event_type == EVENT_DEVICE_OFFLINE:
        return {"status": 2}
    if event_type == EVENT_PROVISION_FAILED:
        return {"reason": "auth_failed", "attempt": seq + 1}
    return {}


def generate_events(
    profile: Dict[str, Any],
    scene: str = "OFFLINE_FLAP",
    count: int = 10,
    start_at: Any = None,
    interval_seconds: int = 30,
    seed: Optional[int] = None,
) -> List[Dict[str, Any]]:
    """生成场景事件流；SHADOW_DIFF 场景每条事件 payload 含完整影子四元组。"""
    if scene not in SCENES:
        raise ValueError(f"unsupported scene: {scene}")
    if count < 0:
        raise ValueError("count must be >= 0")

    start = _parse_time(start_at)
    if start is None:
        start = datetime.now(timezone.utc).replace(microsecond=0)

    rng = random.Random(seed)
    event_types = _scene_event_types(scene, count)
    events: List[Dict[str, Any]] = []
    for i, event_type in enumerate(event_types):
        ts_iso = _iso_utc(start + timedelta(seconds=i * interval_seconds))
        events.append(
            _make_event(
                profile,
                event_type,
                ts_iso,
                seq=i,
                version=i + 1,
                payload=_payload_for(event_type, scene, rng, i + 1, i),
                scene=scene,
            )
        )
    return events


_LIFECYCLE_EVENT_TYPES: Dict[str, str] = {
    "provision": EVENT_PROVISION_SUCCEEDED,
    "activate": EVENT_DEVICE_ONLINE,
    "online": EVENT_DEVICE_ONLINE,
    "telemetry": EVENT_SHADOW_REPORTED_UPDATED,
    "shadow": EVENT_SHADOW_DESIRED_UPDATED,
    "ota": EVENT_SHADOW_DESIRED_UPDATED,
    "offline": EVENT_DEVICE_OFFLINE,
    "reconnect": EVENT_DEVICE_ONLINE,
}


def _lifecycle_payload(stage: str, rng: random.Random, version: int) -> Dict[str, Any]:
    if stage == "provision":
        return {"stage": "provision", "nodeType": None}
    if stage == "activate":
        return {"stage": "activate", "from": 0, "to": 1}
    if stage == "online":
        return {"stage": "online", "status": 1}
    if stage == "telemetry":
        payload = _generate_shadow_state(rng)
        payload["version"] = version
        return {"stage": "telemetry", **payload}
    if stage == "shadow":
        payload = _generate_shadow_state(rng)
        payload["version"] = version
        return {"stage": "shadow", **payload}
    if stage == "ota":
        return {"stage": "ota", "firmwareVersion": "2.0.0", "status": "upgrading"}
    if stage == "offline":
        return {"stage": "offline", "status": 2}
    if stage == "reconnect":
        return {"stage": "reconnect", "status": 1}
    return {"stage": stage}


def generate_lifecycle(
    profile: Dict[str, Any],
    start_at: Any = None,
    interval_seconds: int = 30,
    seed: Optional[int] = None,
) -> List[Dict[str, Any]]:
    """生成完整生命周期事件序列：配网→激活→上线→遥测→影子→OTA→掉线→重连。"""
    start = _parse_time(start_at)
    if start is None:
        start = datetime.now(timezone.utc).replace(microsecond=0)

    rng = random.Random(seed)
    events: List[Dict[str, Any]] = []
    for i, stage in enumerate(LIFECYCLE_STAGES):
        ts_iso = _iso_utc(start + timedelta(seconds=i * interval_seconds))
        event_type = _LIFECYCLE_EVENT_TYPES[stage]
        event = _make_event(
            profile,
            event_type,
            ts_iso,
            seq=i,
            version=i + 1,
            payload=_lifecycle_payload(stage, rng, i + 1),
            scene="LIFECYCLE",
        )
        event["stage"] = stage
        events.append(event)
    return events
