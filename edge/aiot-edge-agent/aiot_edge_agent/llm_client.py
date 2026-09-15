"""OpenAI-compatible 推理客户端与可 mock 的测试替身。"""

from __future__ import annotations

import json
import os
import urllib.error
import urllib.request
from typing import Any, Dict, List, Optional


class LlmClient:
    """OpenAI-compatible Chat Completions 客户端，仅依赖标准库。"""

    def __init__(
        self,
        base_url: Optional[str] = None,
        api_key: Optional[str] = None,
        model: Optional[str] = None,
        timeout: float = 10.0,
    ) -> None:
        self.base_url = base_url or os.environ.get("AIOT_LLM_BASE_URL")
        self.api_key = api_key or os.environ.get("AIOT_LLM_API_KEY")
        self.model = model or os.environ.get("AIOT_LLM_MODEL")
        self.timeout = timeout

    @property
    def is_configured(self) -> bool:
        return bool(self.base_url and self.api_key and self.model)

    def complete_json(self, messages: List[Dict[str, str]]) -> Optional[Dict[str, Any]]:
        content = self._chat(messages)
        if not content:
            return None
        try:
            parsed = json.loads(content)
        except (json.JSONDecodeError, TypeError):
            return None
        return parsed if isinstance(parsed, dict) else None

    def _chat(self, messages: List[Dict[str, str]]) -> Optional[str]:
        if not self.is_configured:
            return None
        url = self.base_url.rstrip("/") + "/chat/completions"
        payload = {
            "model": self.model,
            "messages": messages,
            "temperature": 0.0,
        }
        request = urllib.request.Request(
            url,
            data=json.dumps(payload).encode("utf-8"),
            headers={
                "Content-Type": "application/json",
                "Authorization": f"Bearer {self.api_key}",
            },
            method="POST",
        )
        try:
            with urllib.request.urlopen(request, timeout=self.timeout) as resp:
                data = json.loads(resp.read().decode("utf-8"))
            return data["choices"][0]["message"]["content"]
        except (urllib.error.URLError, KeyError, IndexError, json.JSONDecodeError, TypeError):
            return None


class MockLlmClient:
    """可 mock 的 LLM 客户端：返回调用方指定的 JSON 文档或抛出指定异常。"""

    def __init__(
        self,
        result: Optional[Dict[str, Any]] = None,
        raises: Optional[Exception] = None,
    ) -> None:
        self._result = result
        self._raises = raises

    @property
    def is_configured(self) -> bool:
        return True

    def complete_json(self, messages: List[Dict[str, str]]) -> Optional[Dict[str, Any]]:
        if self._raises is not None:
            raise self._raises
        return self._result
