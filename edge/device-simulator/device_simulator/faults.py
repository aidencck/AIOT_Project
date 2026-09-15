"""故障注入参数生成：依据 failureProfile / networkProfile 产生可复现的故障参数。"""

from __future__ import annotations

import random
from typing import Any, Dict


def inject_failure(profile: Dict[str, Any], rng: random.Random) -> Dict[str, Any]:
    """返回故障注入参数，含故障类型/持续秒数/抖动 rssi/丢包率。"""
    failure_profile = profile.get("failureProfile", {})
    network_profile = profile.get("networkProfile", {})

    failure_types = failure_profile.get("failureTypes", ["OFFLINE_FLAP"])
    failure_type = rng.choice(failure_types)

    base_duration = failure_profile.get("durationSeconds", 120)
    duration_seconds = rng.randint(max(1, base_duration // 2), max(2, base_duration))

    base_rssi = network_profile.get("rssi", -55)
    flapping_rssi = base_rssi - rng.randint(10, 40)

    base_loss = network_profile.get("lossRate", 0.01)
    max_loss = failure_profile.get("maxLossRate", 0.2)
    loss_rate = round(rng.uniform(base_loss, max_loss), 4)

    return {
        "failureType": failure_type,
        "durationSeconds": duration_seconds,
        "flappingRssi": flapping_rssi,
        "lossRate": loss_rate,
    }
