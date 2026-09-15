"""AIoT 模拟设备模块：生成设备画像与 OFFLINE_FLAP 等场景事件流。"""

from .simulator import (
    SCENES,
    emit_jsonl,
    generate_device_profile,
    generate_events,
)

__all__ = [
    "SCENES",
    "emit_jsonl",
    "generate_device_profile",
    "generate_events",
]
