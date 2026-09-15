#!/usr/bin/env python3
"""Sync automated testing roadmap tasks to GitHub Issues/Project."""

from __future__ import annotations

import json
import os
import subprocess
import sys
import tempfile
from typing import Dict, List, Optional


REPO = os.getenv("GITHUB_REPO", "aidencck/AIOT_Project")
OWNER = os.getenv("GITHUB_OWNER", "aidencck")
PROJECT_NUM = os.getenv("GITHUB_PROJECT_NUMBER", "8")
MILESTONE_TITLE = os.getenv("GITHUB_MILESTONE", "测试自动化强化计划")
MILESTONE_DESC = (
    "覆盖事件中台化、质量门禁化、AI最小闭环化的自动化测试任务拆解，"
    "建立可持续回归与发布门禁。"
)

DOC_BASE = f"https://github.com/{REPO}/blob/main"
DOC_LINKS = {
    "blueprint": f"{DOC_BASE}/docs/wiki/ai-native-m0-blueprint.md",
    "backlog": f"{DOC_BASE}/docs/wiki/ai-native-delivery-backlog.md",
    "testing": f"{DOC_BASE}/docs/wiki/testing-and-troubleshooting.md",
    "standards": f"{DOC_BASE}/docs/development_standards.md",
}

LABELS = [
    ("test-automation", "0052cc", "Automated testing implementation"),
    ("quality-gate", "5319e7", "Quality gate and CI guardrails"),
    ("integration-test", "0e8a16", "Cross-service integration tests"),
    ("contract-test", "fbca04", "API contract and schema tests"),
    ("ai-native", "1d76db", "AI-native capability roadmap and delivery"),
]

ISSUES: List[Dict[str, object]] = [
    {
        "title": "[Epic][TEST-AUTO-01] 事件中台化自动化测试体系",
        "labels": ["test-automation", "integration-test", "ai-native"],
        "body": """## 目标
- 建立 `auth-service -> Redis channel -> rule-engine` 的自动化回归测试链路

## 范围
- DeviceEvent 发布字段校验
- 事件消费与反序列化健壮性
- 规则执行触发链路校验

## 验收标准
- [ ] `DEVICE_ONLINE/OFFLINE` 事件结构稳定
- [ ] 非法消息不会导致订阅器异常退出
- [ ] 关键链路在 CI 中可重复通过

## 子任务
- [ ] 编写 `AuthServiceImpl` 事件发布组件测试
- [ ] 编写 `DeviceEventSubscriber` 消费与容错测试
- [ ] 编写 Redis Testcontainers 端到端测试

## 关联文档
- Blueprint: {blueprint}
- Backlog: {backlog}
- Testing Wiki: {testing}
""",
    },
    {
        "title": "[Epic][TEST-AUTO-02] AI最小闭环自动化测试体系",
        "labels": ["test-automation", "contract-test", "ai-native"],
        "body": """## 目标
- 建立规则草案、审批、事件触发执行的自动化测试闭环

## 范围
- 规则生命周期单测
- AI规则 API 契约测试
- 审批前后执行差异验证

## 验收标准
- [ ] `DRAFT -> APPROVED` 状态迁移可验证
- [ ] 未审批规则不会执行
- [ ] API 响应结构具备稳定契约

## 子任务
- [ ] 编写 `RuleLifecycleService` 单元测试
- [ ] 编写 `AiRuleController` 契约测试
- [ ] 增加异常输入与边界场景测试

## 关联文档
- Blueprint: {blueprint}
- Backlog: {backlog}
- Standards: {standards}
""",
    },
    {
        "title": "[Epic][TEST-AUTO-03] 质量门禁与测试治理升级",
        "labels": ["test-automation", "quality-gate"],
        "body": """## 目标
- 将自动化测试分层执行并纳入发布门禁，形成稳定质量基线

## 范围
- 单测/契约/集成测试分层
- 覆盖率报告与阈值策略
- CI 失败阻断策略

## 验收标准
- [ ] PR 阶段默认执行 `unit + contract`
- [ ] 主分支执行 `unit + contract + integration`
- [ ] 覆盖率报告可追踪且阈值可执行

## 子任务
- [ ] Maven Surefire/Failsafe 分层配置
- [ ] JaCoCo 覆盖率聚合与门槛
- [ ] CI 任务依赖与并行策略优化

## 关联文档
- Standards: {standards}
- Testing Wiki: {testing}
""",
    },
    {
        "title": "[Story][TEST-S1] RuleLifecycleService 单元测试落地",
        "labels": ["test-automation", "ai-native"],
        "body": """## 目标
- 覆盖草案生成、审批、事件匹配执行核心逻辑

## 验收标准
- [ ] 默认事件类型与动作负载断言通过
- [ ] 审批后状态为 `APPROVED`
- [ ] 未匹配事件不会触发执行日志
""",
    },
    {
        "title": "[Story][TEST-S2] AiRuleController 契约测试落地",
        "labels": ["test-automation", "contract-test", "ai-native"],
        "body": """## 目标
- 固化 `draft/approve` 接口的请求响应契约

## 验收标准
- [ ] `POST /api/v1/ai/rules/draft` 返回结构稳定
- [ ] `POST /api/v1/ai/rules/{ruleId}/approve` 成功/失败路径可验证
- [ ] 错误输入返回可预期错误消息
""",
    },
    {
        "title": "[Story][TEST-S3] AuthServiceImpl 事件发布组件测试",
        "labels": ["test-automation", "integration-test"],
        "body": """## 目标
- 验证 webhook 触发后 DeviceEvent 发布行为和字段完整性

## 验收标准
- [ ] 上下线动作映射事件类型正确
- [ ] 发布 payload 包含 `eventId/deviceId/timestamp/source`
- [ ] 发布异常被捕获并记录告警
""",
    },
    {
        "title": "[Story][TEST-S4] DeviceEventSubscriber 消费测试",
        "labels": ["test-automation", "integration-test"],
        "body": """## 目标
- 验证事件消费、反序列化和执行器调用行为

## 验收标准
- [ ] 合法消息可触发 `executeByEvent`
- [ ] 非法 JSON 不会中断消费线程
- [ ] 空消息路径有明确日志告警
""",
    },
    {
        "title": "[Story][TEST-S5] Redis Testcontainers 端到端测试",
        "labels": ["test-automation", "integration-test", "quality-gate"],
        "body": """## 目标
- 搭建跨服务最小 E2E 自动化验证（auth -> redis -> rule）

## 验收标准
- [ ] 测试容器可在 CI 拉起并稳定运行
- [ ] 事件从发布到消费全链路通过
- [ ] 失败日志可定位至具体环节
""",
    },
    {
        "title": "[Story][TEST-S6] CI 分层测试与覆盖率门槛",
        "labels": ["test-automation", "quality-gate"],
        "body": """## 目标
- 将测试分组接入 CI 并配置覆盖率阈值

## 验收标准
- [ ] PR 触发 `unit + contract` 并阻断失败
- [ ] 主分支增加 `integration`
- [ ] 覆盖率报告归档并可回看
""",
    },
]


def run(cmd: List[str], *, check: bool = True) -> str:
    result = subprocess.run(cmd, check=check, text=True, capture_output=True)
    if check and result.returncode != 0:
        raise RuntimeError(result.stderr.strip() or result.stdout.strip())
    return (result.stdout or "").strip()


def ensure_label(name: str, color: str, description: str) -> None:
    run(
        [
            "gh",
            "api",
            "-X",
            "POST",
            f"repos/{REPO}/labels",
            "-f",
            f"name={name}",
            "-f",
            f"color={color}",
            "-f",
            f"description={description}",
            "--silent",
        ],
        check=False,
    )


def ensure_milestone() -> int:
    existing = run(
        [
            "gh",
            "api",
            f"repos/{REPO}/milestones",
            "--jq",
            f'.[] | select(.title == "{MILESTONE_TITLE}") | .number',
        ],
        check=False,
    )
    if existing:
        return int(existing.splitlines()[0])
    out = run(
        [
            "gh",
            "api",
            f"repos/{REPO}/milestones",
            "-f",
            f"title={MILESTONE_TITLE}",
            "-f",
            f"description={MILESTONE_DESC}",
            "--jq",
            ".number",
        ]
    )
    return int(out)


def format_body(template: str) -> str:
    return template.format(**DOC_LINKS)


def find_issue_number(title: str) -> Optional[int]:
    out = run(["gh", "issue", "list", "-R", REPO, "--search", title, "--json", "number,title"], check=False)
    if not out:
        return None
    try:
        items = json.loads(out)
    except json.JSONDecodeError:
        return None
    for item in items:
        if item.get("title") == title:
            return int(item["number"])
    return None


def create_or_update_issue(meta: Dict[str, object]) -> str:
    title = str(meta["title"])
    body = format_body(str(meta["body"]))
    labels = ",".join(meta["labels"])
    number = find_issue_number(title)

    with tempfile.NamedTemporaryFile("w", delete=False, suffix=".md", encoding="utf-8") as tmp:
        tmp.write(body)
        body_file = tmp.name

    try:
        if number:
            run(
                [
                    "gh",
                    "issue",
                    "edit",
                    str(number),
                    "-R",
                    REPO,
                    "--body-file",
                    body_file,
                    "--add-label",
                    labels,
                    "--milestone",
                    MILESTONE_TITLE,
                ]
            )
            return f"https://github.com/{REPO}/issues/{number}"

        out = run(
            [
                "gh",
                "issue",
                "create",
                "-R",
                REPO,
                "--title",
                title,
                "--body-file",
                body_file,
                "--label",
                labels,
                "--milestone",
                MILESTONE_TITLE,
            ]
        )
        return out.splitlines()[-1]
    finally:
        os.unlink(body_file)


def ensure_project_access() -> None:
    if not PROJECT_NUM:
        return
    run(["gh", "project", "view", PROJECT_NUM, "--owner", OWNER, "--format", "json"])


def add_issue_to_project(issue_url: str) -> None:
    if not PROJECT_NUM:
        return
    run(["gh", "project", "item-add", PROJECT_NUM, "--owner", OWNER, "--url", issue_url], check=False)


def main() -> int:
    print(f"Syncing testing tasks to {REPO}")
    for label in LABELS:
        ensure_label(*label)

    milestone_num = ensure_milestone()
    print(f"Milestone ready: #{milestone_num} {MILESTONE_TITLE}")
    ensure_project_access()
    if PROJECT_NUM:
        print(f"Project ready: #{PROJECT_NUM}")

    urls: List[str] = []
    for issue in ISSUES:
        url = create_or_update_issue(issue)
        urls.append(url)
        print(f"Synced issue: {url}")
        add_issue_to_project(url)

    print("Sync done. Issue URLs:")
    for url in urls:
        print(f"- {url}")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (RuntimeError, subprocess.CalledProcessError, json.JSONDecodeError, ValueError, KeyError) as exc:
        print(f"Sync failed: {exc}", file=sys.stderr)
        sys.exit(1)
