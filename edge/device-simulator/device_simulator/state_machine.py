"""设备状态机：INACTIVE→ONLINE、ONLINE→OFFLINE、OFFLINE→ONLINE，其余非法。"""

from __future__ import annotations

from enum import IntEnum
from typing import Any


class DeviceStatus(IntEnum):
    INACTIVE = 0
    ONLINE = 1
    OFFLINE = 2


CAN_TRANSITION = {
    DeviceStatus.INACTIVE: frozenset({DeviceStatus.ONLINE}),
    DeviceStatus.ONLINE: frozenset({DeviceStatus.OFFLINE}),
    DeviceStatus.OFFLINE: frozenset({DeviceStatus.ONLINE}),
}


def can_transition(frm: Any, to: Any) -> bool:
    """判断从 frm 状态能否转移到 to 状态。参数可为 DeviceStatus 或 int。"""
    if not isinstance(frm, DeviceStatus):
        frm = DeviceStatus(frm)
    if not isinstance(to, DeviceStatus):
        to = DeviceStatus(to)
    return to in CAN_TRANSITION.get(frm, frozenset())
