#!/usr/bin/env python3
"""拉模式：从 Alertmanager API 拉取 active alerts，幂等同步到 GitHub Issues。

闭环语义（告警出现建单 / 恢复关单）：
  - firing alert 无对应 issue -> 创建（label=alert）
  - firing alert 已有 closed issue -> reopen + 注释
  - 既有 open alert issue 但已不 firing -> 关闭（注释「已恢复」）

去重键 = issue title（由 severity/alertname/instance 稳定构成）。
前置：gh 已登录；Alertmanager 地址需从 CI/调用方可达。
"""
import argparse
import json
import os
import subprocess
import sys
import urllib.error
import urllib.request
from typing import Dict, List, Set

REPO = os.getenv("GITHUB_REPO", "aidencck/AIOT_Project")
LABEL = "alert"
LABEL_COLOR = "d73a4a"


def gh(*args: str, check: bool = True) -> str:
    r = subprocess.run(["gh", *args], check=check, text=True, capture_output=True)
    if check and r.returncode != 0:
        raise RuntimeError(r.stderr.strip() or r.stdout.strip())
    return (r.stdout or "").strip()


def ensure_label(repo: str) -> None:
    gh("api", "-X", "POST", f"repos/{repo}/labels",
       "-f", f"name={LABEL}", "-f", f"color={LABEL_COLOR}",
       "-f", "description=自动告警工单（sync_alerts_to_github 创建）",
       "--silent", check=False)


def fetch_alerts(url: str) -> List[dict]:
    req = urllib.request.Request(f"{url.rstrip('/')}/api/v2/alerts", headers={"Accept": "application/json"})
    with urllib.request.urlopen(req, timeout=15) as resp:
        return json.load(resp)


def alert_title(alert: dict) -> str:
    labels = alert.get("labels", {})
    name = labels.get("alertname", "unknown")
    severity = labels.get("severity", "unknown")
    instance = labels.get("instance") or labels.get("service") or labels.get("job") or ""
    return f"[Alert][{severity}][{name}] {instance}".strip()


def alert_body(alert: dict) -> str:
    labels = alert.get("labels", {})
    annotations = alert.get("annotations", {})
    summary = annotations.get("summary") or annotations.get("description") or ""
    lines = [f"## {summary}".rstrip(), "", "| 项 | 值 |", "| --- | --- |"]
    for k, v in sorted(labels.items()):
        lines.append(f"| {k} | {v} |")
    lines += ["", f"StartsAt: `{alert.get('startsAt', '')}`"]
    return "\n".join(lines)


def list_alert_issues(repo: str) -> Dict[str, Dict[str, object]]:
    out = gh("issue", "list", "-R", repo, "--label", LABEL, "--state", "all",
             "--json", "number,title,state", "--limit", "200", check=False)
    if not out:
        return {}
    try:
        items = json.loads(out)
    except json.JSONDecodeError:
        return {}
    return {it["title"]: it for it in items}


def create_issue(repo: str, title: str, body: str) -> None:
    gh("issue", "create", "-R", repo, "--title", title, "--body", body, "--label", LABEL)


def reopen_issue(repo: str, number: int) -> None:
    gh("issue", "reopen", str(number), "-R", repo, check=False)
    gh("issue", "comment", str(number), "-R", repo, "--body", "告警再次触发，自动重新打开。", check=False)


def close_issue(repo: str, number: int) -> None:
    gh("issue", "close", str(number), "-R", repo, check=False)
    gh("issue", "comment", str(number), "-R", repo, "--body", "告警已恢复，自动关闭。", check=False)


def main() -> int:
    parser = argparse.ArgumentParser(description="Alertmanager active alerts -> GitHub Issues（拉模式）")
    parser.add_argument("--alertmanager-url", default=os.getenv("ALERTMANAGER_URL", "http://localhost:9093"))
    parser.add_argument("--repo", default=REPO)
    args = parser.parse_args()

    repo = args.repo
    ensure_label(repo)

    try:
        alerts = fetch_alerts(args.alertmanager_url)
    except (urllib.error.URLError, OSError, json.JSONDecodeError) as exc:
        print(f"无法访问 Alertmanager {args.alertmanager_url}: {exc}", file=sys.stderr)
        return 1

    firing = [a for a in alerts if a.get("status", {}).get("state") == "active"]
    firing_titles: Set[str] = set()
    created = reopened = closed = 0

    existing = list_alert_issues(repo)
    for alert in firing:
        title = alert_title(alert)
        firing_titles.add(title)
        if title not in existing:
            create_issue(repo, title, alert_body(alert))
            existing[title] = {"number": None, "state": "open"}  # 占位，防同批重复
            created += 1
            print(f"创建: {title}")
        elif existing[title]["state"] == "closed":
            reopen_issue(repo, int(existing[title]["number"]))
            existing[title]["state"] = "open"
            reopened += 1
            print(f"重开: {title}")

    for title, meta in existing.items():
        if title not in firing_titles and meta["state"] == "open":
            close_issue(repo, int(meta["number"]))
            closed += 1
            print(f"关闭（已恢复）: {title}")

    print(f"完成：firing={len(firing)} 创建={created} 重开={reopened} 关闭={closed}")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (RuntimeError, subprocess.CalledProcessError, json.JSONDecodeError, ValueError) as exc:
        print(f"Sync failed: {exc}", file=sys.stderr)
        sys.exit(1)
