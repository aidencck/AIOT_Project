"""设备影子状态生成：reported / desired / delta / version 四元组。"""

from __future__ import annotations

import random
from typing import Any, Dict, Optional


def generate_shadow_state(seed: Optional[int] = None) -> Dict[str, Any]:
    """生成一份影子状态，delta 恒非空（reported 与 desired 存在差异）。"""
    return _generate_shadow_state(random.Random(seed))


def _generate_shadow_state(rng: random.Random) -> Dict[str, Any]:
    reported = {
        "temperature": round(rng.uniform(15.0, 35.0), 1),
        "humidity": round(rng.uniform(30.0, 80.0), 1),
        "powerOn": rng.choice((0, 1)),
    }
    desired = {
        "targetTemp": round(rng.uniform(16.0, 30.0), 1),
        # 强制与 reported 不一致，保证 delta 非空
        "powerOn": 1 - reported["powerOn"],
    }
    delta: Dict[str, Any] = {}
    for key, value in desired.items():
        if key not in reported or reported[key] != value:
            delta[key] = value
    return {
        "reported": reported,
        "desired": desired,
        "delta": delta,
        "version": rng.randint(1, 100),
    }
