"""Exporter source adapters."""

from __future__ import annotations

from abc import ABC, abstractmethod


class SourceAdapter(ABC):
    @abstractmethod
    def export_hash(
        self,
        key: str,
        scene: str | None = None,
        limit: int | None = None,
        offset: int = 0,
    ) -> list[dict]:
        raise NotImplementedError
