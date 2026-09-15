"""诊断结果上报：将 AiDiagnosisResponse 通过 HTTP POST 到云端。

仅依赖标准库 urllib，不引入新依赖。上报失败仅返回 False，不抛异常。
"""

from __future__ import annotations

import json
import os
import urllib.error
import urllib.request
from typing import Any, Dict, Optional

DIAGNOSIS_REPORT_PATH = "/api/v1/ai/diagnosis/report"


class DiagnosisReporter:
    def __init__(
        self,
        report_url: Optional[str] = None,
        timeout: float = 10.0,
    ) -> None:
        self.report_url = report_url or os.environ.get("AIOT_REPORT_URL")
        self.timeout = timeout

    def report(
        self,
        device_id: str,
        scene_type: Optional[str],
        event_id: Optional[str],
        diagnosis: Dict[str, Any],
    ) -> bool:
        """POST 诊断结果到云端；成功返回 True，失败返回 False（不抛异常）。"""
        if not self.report_url:
            return False
        url = self.report_url.rstrip("/") + DIAGNOSIS_REPORT_PATH
        payload = {
            "deviceId": device_id,
            "sceneType": scene_type,
            "eventId": event_id,
            "diagnosis": diagnosis,
        }
        data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        request = urllib.request.Request(
            url,
            data=data,
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        try:
            with urllib.request.urlopen(request, timeout=self.timeout) as response:
                return 200 <= response.status < 300
        except (urllib.error.URLError, urllib.error.HTTPError, OSError):
            return False
