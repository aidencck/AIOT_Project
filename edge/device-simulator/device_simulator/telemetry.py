"""遥测数据生成：按 thingModel 属性产出属性点。"""

from __future__ import annotations

import random
from datetime import datetime, timedelta, timezone
from typing import Any, Dict, List, Optional


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


def _sample_value(prop: Dict[str, Any], rng: random.Random) -> Any:
    ptype = prop.get("type", "float")
    low = prop.get("min", 0)
    high = prop.get("max", 100)
    if ptype in ("bool", "boolean"):
        return int(rng.choice((0, 1)))
    if ptype in ("int", "integer"):
        return rng.randint(int(low), int(high))
    return round(rng.uniform(float(low), float(high)), 2)


def generate_telemetry(
    profile: Dict[str, Any],
    count: int,
    start_at: Any = None,
    interval_seconds: int = 30,
    seed: Optional[int] = None,
) -> List[Dict[str, Any]]:
    """按 thingModel properties 生成遥测点。

    count 为采样次数，每个采样对每个属性产出一个点，
    返回 count * len(properties) 个点，每个点含 propertyKey/value/unit/timestamp/deviceId。
    """
    if count < 0:
        raise ValueError("count must be >= 0")

    start = _parse_time(start_at)
    if start is None:
        start = datetime.now(timezone.utc).replace(microsecond=0)

    rng = random.Random(seed)
    properties = profile["thingModel"]["properties"]
    device_id = profile["globalDeviceId"]

    points: List[Dict[str, Any]] = []
    for i in range(count):
        ts = start + timedelta(seconds=i * interval_seconds)
        ts_iso = ts.astimezone(timezone.utc).isoformat().replace("+00:00", "Z")
        for prop in properties:
            points.append(
                {
                    "propertyKey": prop["propertyKey"],
                    "value": _sample_value(prop, rng),
                    "unit": prop.get("unit", ""),
                    "timestamp": ts_iso,
                    "deviceId": device_id,
                }
            )
    return points
