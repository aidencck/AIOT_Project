"""CLI 入口：python -m aiot_edge_agent --input events.jsonl"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any, Dict, Optional, Sequence

from .agent import EdgeAgent
from .llm_client import LlmClient
from .reporter import DiagnosisReporter


def _read_events(path: Optional[str]):
    if path:
        text = Path(path).read_text(encoding="utf-8")
    else:
        text = sys.stdin.read()
    return [json.loads(line) for line in text.splitlines() if line.strip()]


def _extract_device_id(events: Sequence[Dict[str, Any]], scene_type: Optional[str]) -> str:
    for event in events:
        if event.get("deviceId"):
            return event["deviceId"]
    for event in events:
        if event.get("deviceSn"):
            return event["deviceSn"]
    return scene_type or "OFFLINE"


def _extract_event_id(events: Sequence[Dict[str, Any]]) -> Optional[str]:
    for event in events:
        if event.get("eventId"):
            return event["eventId"]
    return None


def _build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="aiot-edge-agent",
        description="读取设备事件流，输出结构化诊断结果",
    )
    parser.add_argument("--input", type=str, default=None, help="事件流 JSONL 文件，缺省从 stdin 读取")
    parser.add_argument("--output", type=str, default=None, help="诊断结果输出文件，缺省输出到 stdout")
    parser.add_argument("--base-url", type=str, default=None, help="OpenAI-compatible 接口地址")
    parser.add_argument("--api-key", type=str, default=None, help="API Key")
    parser.add_argument("--model", type=str, default=None, help="模型名")
    parser.add_argument("--report-url", type=str, default=None, help="云端诊断上报地址，缺省读环境变量 AIOT_REPORT_URL")
    return parser


def main(argv: Optional[Sequence[str]] = None) -> int:
    args = _build_parser().parse_args(argv)
    events = _read_events(args.input)
    client = LlmClient(base_url=args.base_url, api_key=args.api_key, model=args.model)
    agent = EdgeAgent(llm_client=client)
    diagnosis = agent.diagnose(events)
    text = json.dumps(diagnosis, ensure_ascii=False, indent=2)
    if args.output:
        out = Path(args.output)
        if out.parent and not out.parent.exists():
            out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(text + "\n", encoding="utf-8")
    else:
        print(text)

    reporter = DiagnosisReporter(report_url=args.report_url)
    if reporter.report_url:
        device_id = _extract_device_id(events, diagnosis.get("sceneType"))
        event_id = _extract_event_id(events)
        if not reporter.report(device_id, diagnosis.get("sceneType"), event_id, diagnosis):
            print("警告：诊断结果上报失败", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
