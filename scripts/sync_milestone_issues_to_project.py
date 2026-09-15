#!/usr/bin/env python3
"""把仓库各 milestone 下的 issue 幂等挂载到 GitHub Projects（全量，替代仅覆盖 M0 的旧脚本）。

用法：
  GITHUB_PROJECT_NUMBER=8 python scripts/sync_milestone_issues_to_project.py
  python scripts/sync_milestone_issues_to_project.py --project 8 --milestones "AI-native M0,V1.0 MVP 与基础闭环"

幂等策略：先 `gh project item-list` 取已挂 item 的 issue number 集合，命中则跳过，
避免 `gh project item-add` 重复建 item。缺省遍历仓库全部 open milestone，实现「全量」。
"""
import argparse
import json
import os
import subprocess
import sys
from typing import List, Set

REPO = os.getenv("GITHUB_REPO", "aidencck/AIOT_Project")
OWNER = os.getenv("GITHUB_OWNER", "aidencck")
PROJECT = os.getenv("GITHUB_PROJECT_NUMBER", "8")


def run(cmd: List[str], *, check: bool = True) -> str:
    r = subprocess.run(cmd, check=check, text=True, capture_output=True)
    if check and r.returncode != 0:
        raise RuntimeError(r.stderr.strip() or r.stdout.strip())
    return (r.stdout or "").strip()


def list_milestone_titles(repo: str) -> List[str]:
    out = run(["gh", "api", f"repos/{repo}/milestones", "--jq", ".[].title"], check=False)
    return [t for t in out.splitlines() if t] if out else []


def list_issue_numbers_in_milestone(repo: str, title: str) -> List[int]:
    out = run(
        [
            "gh", "issue", "list", "-R", repo,
            "--milestone", title, "--state", "all",
            "--json", "number", "--limit", "200",
        ],
        check=False,
    )
    if not out:
        return []
    try:
        return [int(it["number"]) for it in json.loads(out)]
    except (json.JSONDecodeError, ValueError):
        return []


def list_existing_item_numbers(project: str, owner: str) -> Set[int]:
    out = run(
        ["gh", "project", "item-list", project, "--owner", owner,
         "--limit", "200", "--format", "json"],
        check=False,
    )
    if not out:
        return set()
    try:
        items = json.loads(out).get("items", [])
    except json.JSONDecodeError:
        return set()
    return {int(it["content"]["number"]) for it in items if it.get("content", {}).get("number")}


def add_issue_to_project(repo: str, project: str, owner: str, number: int) -> None:
    url = f"https://github.com/{repo}/issues/{number}"
    run(["gh", "project", "item-add", project, "--owner", owner, "--url", url], check=False)


def main() -> int:
    parser = argparse.ArgumentParser(description="幂等把 milestone issue 挂到 GitHub Project")
    parser.add_argument("--milestones", help="逗号分隔的 milestone 标题；缺省=全部 open milestone")
    parser.add_argument("--project", default=PROJECT, help="GitHub Project number")
    parser.add_argument("--owner", default=OWNER)
    parser.add_argument("--repo", default=REPO)
    args = parser.parse_args()

    project = args.project
    owner = args.owner
    repo = args.repo

    titles = [t.strip() for t in args.milestones.split(",") if t.strip()] if args.milestones else list_milestone_titles(repo)
    if not titles:
        print("未找到任何 milestone（--milestones 为空且仓库无 open milestone）", file=sys.stderr)
        return 1

    existing = list_existing_item_numbers(project, owner)
    print(f"Project #{project} 已挂 {len(existing)} 个 issue item")

    added = skipped = 0
    for title in titles:
        numbers = list_issue_numbers_in_milestone(repo, title)
        print(f"milestone `{title}`: {len(numbers)} issues")
        for n in numbers:
            if n in existing:
                skipped += 1
                continue
            add_issue_to_project(repo, project, owner, n)
            existing.add(n)
            added += 1

    print(f"完成：新增 {added}，跳过已存在 {skipped}")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (RuntimeError, subprocess.CalledProcessError, json.JSONDecodeError, ValueError) as exc:
        print(f"Sync failed: {exc}", file=sys.stderr)
        sys.exit(1)
