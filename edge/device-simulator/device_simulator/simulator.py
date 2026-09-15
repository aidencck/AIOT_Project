"""模拟设备核心逻辑：确定性设备画像与事件流生成。

仅依赖标准库，不依赖真实 EMQX / 硬件。事件 eventId 为确定性幂等键：
相同 (deviceSn, occurredAt, eventType) 始终生成相同 eventId。
"""

from __future__ import annotations

import hashlib
import json
import random
import sys
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any, Dict, Iterable, List, Optional, Sequence

SCENES: Sequence[str] = ("OFFLINE_FLAP", "PROVISION_FAILURE", "SHADOW_DIFF")

EVENT_ONLINE = "DEVICE_ONLINE"
EVENT_OFFLINE = "DEVICE_OFFLINE"
EVENT_PROVISION_FAILED = "DEVICE_PROVISION_FAILED"
EVENT_SHADOW_DESIRED = "SHADOW_DESIRED_UPDATED"
EVENT_SHADOW_REPORTED = "SHADOW_REPORTED_UPDATED"

_DEFAULT_PRODUCT_KEYS = ("a1SmartDesk", "a1IceMaker", "a1AirPurifier")
_DEFAULT_FIRMWARE_VERSIONS = ("1.0.0", "1.1.2", "2.0.0")


def generate_device_profile(seed: Optional[int] = None) -> Dict[str, Any]:
    """生成一份设备画像，包含 productKey / deviceSn / homeId / firmwareVersion。"""
    rng = random.Random(seed)
    product_key = rng.choice(_DEFAULT_PRODUCT_KEYS)
    device_sn = f"{product_key}{rng.randint(100000, 999999):06d}"
    home_id = f"home-{rng.randint(10000, 99999)}"
    firmware_version = rng.choice(_DEFAULT_FIRMWARE_VERSIONS)
    return {
        "productKey": product_key,
        "deviceSn": device_sn,
        "homeId": home_id,
        "firmwareVersion": firmware_version,
    }


def event_id(device_sn: str, occurred_at: str, event_type: str) -> str:
    """确定性幂等 eventId：同一设备同一时刻同一事件类型生成相同 id。"""
    digest = hashlib.sha1(
        f"{device_sn}|{occurred_at}|{event_type}".encode("utf-8")
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


def _status_sequence(scene: str, count: int) -> List[str]:
    if scene == "OFFLINE_FLAP":
        return [EVENT_ONLINE if i % 2 == 0 else EVENT_OFFLINE for i in range(count)]
    if scene == "PROVISION_FAILURE":
        return [EVENT_PROVISION_FAILED] * count
    if scene == "SHADOW_DIFF":
        return [
            EVENT_SHADOW_DESIRED if i % 2 == 0 else EVENT_SHADOW_REPORTED
            for i in range(count)
        ]
    raise ValueError(f"unsupported scene: {scene}")


def generate_events(
    profile: Dict[str, Any],
    scene: str = "OFFLINE_FLAP",
    count: int = 10,
    start_at: Any = None,
    interval_seconds: int = 30,
    seed: Optional[int] = None,
) -> List[Dict[str, Any]]:
    """生成场景事件流。OFFLINE_FLAP 场景在在线/离线之间翻转。"""
    if scene not in SCENES:
        raise ValueError(f"unsupported scene: {scene}")
    if count < 0:
        raise ValueError("count must be >= 0")

    start = _parse_time(start_at)
    if start is None:
        start = datetime.now(timezone.utc).replace(microsecond=0)

    device_sn = profile["deviceSn"]
    statuses = _status_sequence(scene, count)
    events: List[Dict[str, Any]] = []
    for i in range(count):
        occurred_at = start + timedelta(seconds=i * interval_seconds)
        occurred_at_iso = occurred_at.astimezone(timezone.utc).isoformat().replace("+00:00", "Z")
        event_type = statuses[i]
        events.append(
            {
                "eventId": event_id(device_sn, occurred_at_iso, event_type),
                "sceneType": scene,
                "eventType": event_type,
                "productKey": profile["productKey"],
                "deviceSn": device_sn,
                "homeId": profile["homeId"],
                "firmwareVersion": profile["firmwareVersion"],
                "occurredAt": occurred_at_iso,
                "seq": i,
            }
        )
    return events


def emit_jsonl(events: Iterable[Dict[str, Any]], output: Optional[Path] = None) -> None:
    """将事件流输出为 JSONL；output 为 None 时写入 stdout。"""
    lines = [json.dumps(e, ensure_ascii=False) for e in events]
    text = "\n".join(lines) + ("\n" if lines else "")
    if output is None:
        sys.stdout.write(text)
        return
    output = Path(output)
    if output.parent and not output.parent.exists():
        output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(text, encoding="utf-8")
