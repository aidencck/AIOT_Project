"""CLI 入口：python -m device_simulator --scene OFFLINE_FLAP --count 10"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Optional, Sequence

from .reporter import HttpIngestReporter
from .simulator import (
    SCENES,
    emit_jsonl,
    generate_device_profile,
    generate_events,
)


def _build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="device-simulator",
        description="生成模拟设备画像并输出场景事件流（JSONL）",
    )
    parser.add_argument("--scene", default="OFFLINE_FLAP", choices=SCENES, help="场景类型")
    parser.add_argument("--count", type=int, default=10, help="事件条数")
    parser.add_argument("--output", type=str, default=None, help="JSONL 输出文件路径，缺省输出到 stdout")
    parser.add_argument("--profile", type=str, default=None, help="将 device_profile 写入该 JSON 文件")
    parser.add_argument("--seed", type=int, default=42, help="随机种子（保证可复现）")
    parser.add_argument("--start-at", type=str, default=None, help="起始时间（ISO8601），缺省使用当前 UTC 时间")
    parser.add_argument("--interval-seconds", type=int, default=30, help="相邻事件间隔秒数")
    parser.add_argument("--ingest-url", type=str, default=None, help="云端 mqtt-adapter 入站地址，缺省读环境变量 AIOT_INGEST_URL")
    parser.add_argument("--internal-token", type=str, default=None, help="内部令牌，缺省读环境变量 AIOT_INTERNAL_TOKEN")
    return parser


def main(argv: Optional[Sequence[str]] = None) -> int:
    args = _build_parser().parse_args(argv)

    profile = generate_device_profile(args.seed)
    if args.profile:
        profile_path = Path(args.profile)
        if profile_path.parent and not profile_path.parent.exists():
            profile_path.parent.mkdir(parents=True, exist_ok=True)
        profile_path.write_text(json.dumps(profile, ensure_ascii=False, indent=2), encoding="utf-8")

    events = generate_events(
        profile,
        scene=args.scene,
        count=args.count,
        start_at=args.start_at,
        interval_seconds=args.interval_seconds,
        seed=args.seed,
    )
    emit_jsonl(events, Path(args.output) if args.output else None)

    reporter = HttpIngestReporter(ingest_url=args.ingest_url, internal_token=args.internal_token)
    if reporter.ingest_url:
        for event in events:
            if not reporter.publish(event):
                print(f"警告：事件上报失败 {event.get('eventId')}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
