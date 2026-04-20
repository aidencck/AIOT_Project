#!/usr/bin/env python3
"""Seed AI-native M0 milestones and issues into GitHub Issues/Projects."""
import json
import os
import subprocess
import sys
import tempfile
from typing import Dict, List, Optional


REPO = os.getenv("GITHUB_REPO", "aidencck/AIOT_Project")
OWNER = os.getenv("GITHUB_OWNER", "aidencck")
PROJECT_NUM = os.getenv("GITHUB_PROJECT_NUMBER", "")
MILESTONE_TITLE = "AI-native M0"
MILESTONE_DESC = "AI-native 第一阶段最小闭环：事件中心、规则中心、知识中心、设备诊断 Copilot、审批执行"
DEFAULT_STATUS = os.getenv("GITHUB_PROJECT_STATUS", "Todo")
BASE_URL = f"https://github.com/{REPO}/blob/main"

DOC_LINKS = {
    "overview": f"{BASE_URL}/docs/wiki/ai-native-overview.md",
    "blueprint": f"{BASE_URL}/docs/wiki/ai-native-m0-blueprint.md",
    "backlog": f"{BASE_URL}/docs/wiki/ai-native-delivery-backlog.md",
    "roadmap": f"{BASE_URL}/docs/product/AIoT_AI_Native_Product_Roadmap.md",
}

LABELS = [
    ("ai-native", "1d76db", "AI-native capability roadmap and delivery"),
    ("wiki-linked", "5319e7", "Linked to local wiki design documents"),
    ("epic", "b60205", "Epic level planning item"),
    ("story", "0e8a16", "Story level delivery item"),
    ("backend", "0052cc", "Backend delivery scope"),
    ("sprint-1", "d4c5f9", "AI-native M0 Sprint 1"),
    ("sprint-2", "c2e0c6", "AI-native M0 Sprint 2"),
    ("sprint-3", "f9d0c4", "AI-native M0 Sprint 3"),
    ("sprint-4", "bfdadc", "AI-native M0 Sprint 4"),
]

ISSUES: List[Dict[str, object]] = [
    {
        "title": "[Epic][AIN-01] 事件中心与 DeviceEvent 统一建模",
        "labels": ["ai-native", "epic", "backend", "wiki-linked"],
        "body": """## 目标
- 建立统一 `DeviceEvent` 模型，承载设备上下线、影子变化、控制结果等关键事件

## 范围
- 统一事件结构
- 事件落表与查询
- 事件接入规范

## 验收标准
- [ ] 关键事件具备统一字段结构
- [ ] 支持按 `device_id/home_id/event_type` 查询
- [ ] 具备 TraceId 与审计字段

## 关联文档
- Wiki: {overview}
- Blueprint: {blueprint}
- Backlog: {backlog}
""",
    },
    {
        "title": "[Epic][AIN-02] 规则中心最小化产品化",
        "labels": ["ai-native", "epic", "backend", "wiki-linked"],
        "body": """## 目标
- 建立阈值、离线、频发三类规则的定义、执行、预览与命中日志能力

## 范围
- 规则定义表
- 规则执行器
- 规则命中日志
- 规则预览接口

## 验收标准
- [ ] 三类规则可配置并生效
- [ ] 命中结果可追踪
- [ ] 规则预览接口可返回风险提示

## 关联文档
- Wiki: {overview}
- Blueprint: {blueprint}
- Backlog: {backlog}
""",
    },
    {
        "title": "[Epic][AIN-03] 设备知识中心与检索构建",
        "labels": ["ai-native", "epic", "backend", "wiki-linked"],
        "body": """## 目标
- 基于 `thingModelJson`、FAQ、SOP 构建设备知识切片与检索能力

## 范围
- 文档导入
- 知识切片
- 索引构建
- 检索调试接口

## 验收标准
- [ ] 支持设备知识导入与切片
- [ ] 检索结果可返回来源信息
- [ ] 诊断请求可获得有效知识片段

## 关联文档
- Wiki: {overview}
- Roadmap: {roadmap}
- Backlog: {backlog}
""",
    },
    {
        "title": "[Epic][AIN-04] 设备诊断 Copilot MVP",
        "labels": ["ai-native", "epic", "backend", "wiki-linked"],
        "body": """## 目标
- 提供设备诊断问答、状态解释、规则草案生成的最小闭环能力

## 范围
- 会话管理
- 诊断消息接口
- 规则草案生成
- 答案结构化输出

## 验收标准
- [ ] 至少覆盖 10 个高频问题模板
- [ ] 诊断结果带引用来源
- [ ] 可生成规则草案并返回风险等级

## 关联文档
- Wiki: {overview}
- Blueprint: {blueprint}
- Roadmap: {roadmap}
""",
    },
    {
        "title": "[Epic][AIN-05] 审批执行与审计闭环",
        "labels": ["ai-native", "epic", "backend", "wiki-linked"],
        "body": """## 目标
- 建立规则草案审批、执行记录和审计追踪能力

## 范围
- 审批状态流
- 执行记录
- 审计追踪

## 验收标准
- [ ] 高风险动作必须经过审批
- [ ] 审批与执行具备完整日志
- [ ] 结果可回写到业务链路

## 关联文档
- Wiki: {overview}
- Blueprint: {blueprint}
- Backlog: {backlog}
""",
    },
    {
        "title": "[Story][AIN-S1] Sprint 1 完成事件底座",
        "labels": ["ai-native", "story", "backend", "wiki-linked", "sprint-1"],
        "body": """## 目标
- 完成 `DeviceEvent` 统一建模、落表和查询能力

## 子任务
- [ ] 设计 `DeviceEvent` 结构
- [ ] 新增 `ai_device_event` 表
- [ ] 接入设备上下线事件
- [ ] 接入影子变化事件
- [ ] 提供事件查询 API

## 验收标准
- [ ] 事件数据可查询可追踪
- [ ] TraceId 与审计字段完整

## 关联文档
- Blueprint: {blueprint}
- Backlog: {backlog}
""",
    },
    {
        "title": "[Story][AIN-S2] Sprint 2 完成规则中心最小可用版本",
        "labels": ["ai-native", "story", "backend", "wiki-linked", "sprint-2"],
        "body": """## 目标
- 完成规则定义、执行、命中日志与预览接口

## 子任务
- [ ] 新增 `ai_rule_definition`
- [ ] 新增 `ai_rule_execution_log`
- [ ] 实现阈值规则执行器
- [ ] 实现离线规则执行器
- [ ] 实现频发规则执行器
- [ ] 实现规则预览接口

## 验收标准
- [ ] 三类规则可稳定运行
- [ ] 命中日志完整可追踪

## 关联文档
- Blueprint: {blueprint}
- Backlog: {backlog}
""",
    },
    {
        "title": "[Story][AIN-S3] Sprint 3 完成知识中心与检索能力",
        "labels": ["ai-native", "story", "backend", "wiki-linked", "sprint-3"],
        "body": """## 目标
- 完成物模型知识切片、FAQ/SOP 导入与检索接口

## 子任务
- [ ] 解析 `thingModelJson`
- [ ] 导入 FAQ / SOP 文档
- [ ] 新增 `ai_knowledge_document`
- [ ] 新增 `ai_knowledge_chunk`
- [ ] 实现切片和索引构建任务
- [ ] 实现知识检索调试接口

## 验收标准
- [ ] 检索返回可解释来源
- [ ] 诊断上下文可命中知识片段

## 关联文档
- Roadmap: {roadmap}
- Backlog: {backlog}
""",
    },
    {
        "title": "[Story][AIN-S4] Sprint 4 完成 Copilot 与审批闭环",
        "labels": ["ai-native", "story", "backend", "wiki-linked", "sprint-4"],
        "body": """## 目标
- 完成会话、诊断、规则草案、审批执行最小闭环

## 子任务
- [ ] 新增 `ai_copilot_session`
- [ ] 新增 `ai_copilot_message`
- [ ] 实现会话与消息接口
- [ ] 实现诊断编排
- [ ] 实现规则草案生成
- [ ] 实现审批与执行记录

## 验收标准
- [ ] 诊断可返回结构化结论
- [ ] 草案可提交审批并记录执行过程

## 关联文档
- Blueprint: {blueprint}
- Backlog: {backlog}
""",
    },
]


def run(cmd: List[str]) -> str:
    """Run a command and return trimmed stdout."""
    result = subprocess.run(cmd, check=True, text=True, capture_output=True)
    return result.stdout.strip()


def try_run(cmd: List[str]) -> str:
    """Run a command without raising automatically, then normalize errors."""
    result = subprocess.run(cmd, check=False, text=True, capture_output=True)
    if result.returncode != 0:
        raise RuntimeError(result.stderr.strip() or result.stdout.strip())
    return result.stdout.strip()


def ensure_label(name: str, color: str, description: str) -> None:
    """Create a label if it does not exist yet."""
    subprocess.run(
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
        text=True,
        capture_output=True,
    )


def ensure_milestone() -> int:
    """Create the AI-native milestone if missing and return its number."""
    existing = run(
        [
            "gh",
            "api",
            f"repos/{REPO}/milestones",
            "--jq",
            f'.[] | select(.title == "{MILESTONE_TITLE}") | .number',
        ]
    )
    if existing:
        return int(existing.splitlines()[0])
    return int(
        run(
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
    )


def format_body(template: str) -> str:
    """Inject canonical documentation links into an issue body."""
    return template.format(**DOC_LINKS)


def find_issue_number(title: str) -> Optional[int]:
    """Find an existing issue by exact title."""
    out = run(["gh", "issue", "list", "-R", REPO, "--search", title, "--json", "number,title"])
    items = json.loads(out or "[]")
    for item in items:
        if item.get("title") == title:
            return int(item["number"])
    return None


def create_or_update_issue(meta: Dict[str, object]) -> str:
    """Create or update an issue and return its URL."""
    title = str(meta["title"])
    body = format_body(str(meta["body"]))
    labels = ",".join(meta["labels"])
    existing = find_issue_number(title)
    if existing:
        with tempfile.NamedTemporaryFile("w", delete=False, suffix=".md", encoding="utf-8") as tmp:
            tmp.write(body)
            temp_path = tmp.name
        try:
            subprocess.run(
                [
                    "gh",
                    "issue",
                    "edit",
                    str(existing),
                    "-R",
                    REPO,
                    "--body-file",
                    temp_path,
                    "--add-label",
                    labels,
                    "--milestone",
                    MILESTONE_TITLE,
                ],
                check=True,
            )
        finally:
            os.unlink(temp_path)
        return f"https://github.com/{REPO}/issues/{existing}"

    with tempfile.NamedTemporaryFile("w", delete=False, suffix=".md", encoding="utf-8") as tmp:
        tmp.write(body)
        temp_path = tmp.name
    try:
        url = run(
            [
                "gh",
                "issue",
                "create",
                "-R",
                REPO,
                "--title",
                title,
                "--body-file",
                temp_path,
                "--label",
                labels,
                "--milestone",
                MILESTONE_TITLE,
            ]
        )
        return url.splitlines()[-1]
    finally:
        os.unlink(temp_path)


def add_issue_to_project(project_num: str, issue_url: str) -> None:
    """Add an issue URL to GitHub Projects."""
    subprocess.run(
        ["gh", "project", "item-add", project_num, "--owner", OWNER, "--url", issue_url],
        check=True,
    )


def ensure_project_access(project_num: str) -> None:
    """Verify that the target GitHub Project is reachable."""
    try_run(["gh", "project", "view", project_num, "--owner", OWNER, "--format", "json"])


def main() -> int:
    """Sync milestone, labels, issues, and optional project items."""
    print(f"Syncing AI-native M0 tasks to {REPO}")
    for label in LABELS:
        ensure_label(*label)

    milestone_num = ensure_milestone()
    print(f"Milestone ready: #{milestone_num} {MILESTONE_TITLE}")

    project_enabled = bool(PROJECT_NUM)
    if project_enabled:
        ensure_project_access(PROJECT_NUM)
        print(f"Project ready: #{PROJECT_NUM}")

    created_urls = []
    for issue in ISSUES:
        issue_url = create_or_update_issue(issue)
        created_urls.append(issue_url)
        print(f"Synced issue: {issue_url}")
        if project_enabled:
            add_issue_to_project(PROJECT_NUM, issue_url)
            print(f"Added to project #{PROJECT_NUM}: {issue_url}")

    print("AI-native M0 sync finished.")
    print("Issue URLs:")
    for url in created_urls:
        print(f"- {url}")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (RuntimeError, subprocess.CalledProcessError, json.JSONDecodeError, KeyError, ValueError) as exc:
        print(f"Sync failed: {exc}", file=sys.stderr)
        sys.exit(1)
