"""HTTP 入站上报：将模拟设备事件 POST 到云端 mqtt-adapter 入站接口。

仅依赖标准库 urllib，不引入新依赖。上报失败仅返回 False，不抛异常。
"""

from __future__ import annotations

import json
import os
import urllib.error
import urllib.request
from datetime import datetime, timezone
from typing import Any, Dict, Optional

INGEST_PATH = "/api/v1/mqtt/messages"


class HttpIngestReporter:
    def __init__(
        self,
        ingest_url: Optional[str] = None,
        internal_token: Optional[str] = None,
        timeout: float = 10.0,
    ) -> None:
        self.ingest_url = ingest_url or os.environ.get("AIOT_INGEST_URL")
        self.internal_token = internal_token or os.environ.get("AIOT_INTERNAL_TOKEN")
        self.timeout = timeout

    def publish(self, event: Dict[str, Any]) -> bool:
        """POST 单条事件到云端入站接口；成功返回 True，失败返回 False（不抛异常）。"""
        if not self.ingest_url:
            return False
        url = self.ingest_url.rstrip("/") + INGEST_PATH
        data = json.dumps(self._build_body(event), ensure_ascii=False).encode("utf-8")
        headers = {"Content-Type": "application/json"}
        if self.internal_token:
            headers["X-Internal-Token"] = self.internal_token
        request = urllib.request.Request(
            url,
            data=data,
            headers=headers,
            method="POST",
        )
        try:
            with urllib.request.urlopen(request, timeout=self.timeout) as response:
                return 200 <= response.status < 300
        except (urllib.error.URLError, urllib.error.HTTPError, OSError):
            return False

    def _build_body(self, event: Dict[str, Any]) -> Dict[str, Any]:
        device_id = event.get("deviceId") or event.get("deviceSn")
        return {
            "messageId": event.get("eventId"),
            "deviceId": device_id,
            "topic": f"devices/{device_id}/status",
            "payload": json.dumps(event, ensure_ascii=False),
            "timestamp": self._to_epoch_millis(event.get("occurredAt")),
        }

    @staticmethod
    def _to_epoch_millis(occurred_at: Any) -> Optional[int]:
        if not occurred_at:
            return None
        try:
            text = str(occurred_at)
            if text.endswith("Z"):
                text = text[:-1] + "+00:00"
            dt = datetime.fromisoformat(text)
            if dt.tzinfo is None:
                dt = dt.replace(tzinfo=timezone.utc)
            return int(dt.timestamp() * 1000)
        except (ValueError, OverflowError, OSError):
            return None
