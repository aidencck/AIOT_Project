"""确定性设备画像生成：对齐 device_info / device_credential / product_info 契约。

device_info 12 字段：
    id, globalDeviceId, deviceName, productKey, deviceSn, authIdentity,
    status, homeId, roomId, gatewayId, firmwareVersion, lastHeartbeatTime
其中 status 取值 0=INACTIVE, 1=ONLINE, 2=OFFLINE。

额外字段（供模拟器使用，非 device_info 表字段）：
    deviceSecret, nodeType, thingModel, telemetryTemplate, failureProfile, networkProfile
"""

from __future__ import annotations

import hashlib
import random
from typing import Any, Dict, Optional

_PRODUCT_KEYS = ("a1SmartDesk", "a1IceMaker", "a1AirPurifier")
_FIRMWARE_VERSIONS = ("1.0.0", "1.1.2", "2.0.0")

_THING_PROPERTIES = (
    {
        "propertyKey": "temperature",
        "name": "temperature",
        "unit": "celsius",
        "type": "float",
        "min": -20.0,
        "max": 80.0,
    },
    {
        "propertyKey": "humidity",
        "name": "humidity",
        "unit": "percent",
        "type": "float",
        "min": 0.0,
        "max": 100.0,
    },
    {
        "propertyKey": "powerOn",
        "name": "powerOn",
        "unit": "",
        "type": "bool",
        "min": 0,
        "max": 1,
    },
    {
        "propertyKey": "targetTemp",
        "name": "targetTemp",
        "unit": "celsius",
        "type": "float",
        "min": -20.0,
        "max": 80.0,
    },
)


def generate_device_profile(seed: Optional[int] = None) -> Dict[str, Any]:
    """生成一份确定性设备画像（同 seed 同结果）。"""
    rng = random.Random(seed)

    product_key = rng.choice(_PRODUCT_KEYS)
    device_sn = f"{product_key}{rng.randint(100000, 999999):06d}"
    global_device_id = f"gdid-{device_sn}"
    node_type = rng.choice((1, 2, 3))
    device_secret = hashlib.sha1(
        f"secret-{device_sn}-{rng.randint(0, 1 << 32)}".encode("utf-8")
    ).hexdigest()[:32]

    profile: Dict[str, Any] = {
        # device_info 12 字段
        "id": rng.randint(1, 1 << 31),
        "globalDeviceId": global_device_id,
        "deviceName": f"Device-{device_sn[-6:]}",
        "productKey": product_key,
        "deviceSn": device_sn,
        "authIdentity": device_sn,
        "status": 0,  # INACTIVE 初始状态
        "homeId": f"home-{rng.randint(10000, 99999)}",
        "roomId": f"room-{rng.randint(1, 99)}",
        "gatewayId": f"gw-{rng.randint(1000, 9999)}",
        "firmwareVersion": rng.choice(_FIRMWARE_VERSIONS),
        "lastHeartbeatTime": None,
        # 扩展字段
        "deviceSecret": device_secret,
        "nodeType": node_type,
        "thingModel": {
            "properties": list(_THING_PROPERTIES),
            "services": [],
            "events": [],
        },
        "telemetryTemplate": {
            "intervalSeconds": 30,
            "properties": list(_THING_PROPERTIES),
        },
        "failureProfile": {
            "failureTypes": ["OFFLINE_FLAP", "PROVISION_FAILURE", "SHADOW_DIFF"],
            "durationSeconds": 120,
            "flappingEnabled": True,
            "maxLossRate": 0.2,
        },
        "networkProfile": {
            "rssi": -55,
            "lossRate": 0.01,
            "latencyMs": 25,
        },
    }
    return profile
