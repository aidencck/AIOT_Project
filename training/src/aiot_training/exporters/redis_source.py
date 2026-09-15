"""Redis source adapter for current M0 runtime storage."""

from __future__ import annotations

import json
from typing import Any

from .base import SourceAdapter

DIAGNOSIS_KEY = "aiot:ai:diagnosis-records"
FEEDBACK_KEY = "aiot:ai:feedback-records"


class RedisSourceAdapter(SourceAdapter):
    def __init__(self, client: Any):
        self.client = client

    def export_hash(
        self,
        key: str,
        scene: str | None = None,
        limit: int | None = None,
        offset: int = 0,
    ) -> list[dict]:
        result: list[dict] = []
        diagnosis_scene_map = self._build_diagnosis_scene_map() if scene and key == FEEDBACK_KEY else {}
        for _, value in self.client.hgetall(key).items():
            row = self._parse_row(value)
            if scene and not self._match_scene(row, scene, diagnosis_scene_map):
                continue
            result.append(row)
        if offset:
            result = result[offset:]
        if limit is not None and limit >= 0:
            result = result[:limit]
        return result

    @staticmethod
    def _parse_row(value: Any) -> dict:
        payload = value.decode("utf-8") if isinstance(value, bytes) else str(value)
        return json.loads(payload)

    def _build_diagnosis_scene_map(self) -> dict[str, str]:
        scene_map: dict[str, str] = {}
        for _, value in self.client.hgetall(DIAGNOSIS_KEY).items():
            row = self._parse_row(value)
            diagnosis_id = row.get("diagnosisId") or row.get("diagnosis_id")
            scene = row.get("sceneType") or row.get("scene_type")
            if diagnosis_id and scene:
                scene_map[str(diagnosis_id)] = str(scene)
        return scene_map

    def _match_scene(self, row: dict, scene: str, diagnosis_scene_map: dict[str, str] | None = None) -> bool:
        normalized = scene.upper()
        direct_scene = row.get("sceneType") or row.get("scene_type")
        if direct_scene is not None:
            return direct_scene == normalized
        diagnosis_id = row.get("diagnosisId") or row.get("diagnosis_id")
        if diagnosis_id is None or not diagnosis_scene_map:
            return False
        return diagnosis_scene_map.get(str(diagnosis_id)) == normalized
