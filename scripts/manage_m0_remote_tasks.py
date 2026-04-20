#!/usr/bin/env python3
import json
import os
import subprocess
import tempfile

REPO = "aidencck/AIOT_Project"
OWNER = "aidencck"
PROJECT_NUM = "8"

ISSUES = {
    65: {
        "estimate": "4",
        "scope": ["角色、菜单、数据权限（项目/家庭维度）", "关键操作二次确认能力", "权限校验规则接入现有接口"],
        "acc": ["不同角色访问菜单与接口权限正确", "家庭/项目数据范围隔离生效", "高风险操作触发二次确认并记录日志"],
        "weekly": "本周进行中",
    },
    66: {
        "estimate": "5",
        "scope": ["家庭创建、成员管理、关系维护", "设备绑定/解绑流程与校验", "用户-家庭-设备主数据一致性"],
        "acc": ["可完成家庭创建与成员维护", "设备绑定/解绑流程闭环且无越权", "主数据一致性校验通过"],
        "weekly": "本周进行中",
    },
    67: {
        "estimate": "4",
        "scope": ["设备台账查询与筛选", "在线状态、固件版本、最后心跳展示", "资产信息基础维护能力"],
        "acc": ["可按家庭/项目查询设备台账", "在线状态与心跳数据实时可见", "版本与资产字段可维护并可追溯"],
        "weekly": "本周进行中",
    },
    68: {
        "estimate": "4",
        "scope": ["规则模板支持阈值、离线、频发、去重、升级", "规则启停与版本管理（基础）", "规则命中日志输出"],
        "acc": ["五类规则模板可配置并生效", "规则命中后产生标准化事件", "规则启停变更可审计"],
        "weekly": "Backlog",
    },
    69: {
        "estimate": "5",
        "scope": ["告警入池、级别标注、状态流转", "告警检索（按设备/家庭/级别/时间）", "告警与规则、设备、家庭关联"],
        "acc": ["告警可进入待处理队列", "状态流转链路完整（新建/处理中/已恢复）", "关键维度检索性能满足日常运营"],
        "weekly": "Backlog",
    },
    70: {
        "estimate": "3",
        "scope": ["接入至少1条通知通道", "通知模板与变量渲染", "失败重试与发送结果追踪"],
        "acc": ["告警触发后可成功发送通知", "模板变量渲染正确", "发送失败具备重试与失败记录"],
        "weekly": "Backlog",
    },
    71: {
        "estimate": "3",
        "scope": ["按告警级别与类型自动建单", "工单与告警双向关联", "建单策略可配置（基础）"],
        "acc": ["告警自动建单成功率 >= 95%", "工单与告警可互相追踪", "建单策略调整后即时生效"],
        "weekly": "Backlog",
    },
    72: {
        "estimate": "5",
        "scope": ["手动/规则分派", "处理动作、处理结论、结案流程", "处理结果回写告警中心"],
        "acc": ["工单分派与转派流程可用", "处理记录结构化并可审计", "结案后结果回写成功"],
        "weekly": "Backlog",
    },
    73: {
        "estimate": "3",
        "scope": ["响应时长、处理时长统计", "超时预警规则", "SLA看板基础指标"],
        "acc": ["P1工单响应时长可计算", "超时工单自动触发提醒", "SLA指标可在看板展示"],
        "weekly": "Backlog",
    },
    74: {
        "estimate": "4",
        "scope": ["关键操作日志、权限变更日志、异常登录日志", "审计检索与导出（基础）", "高风险事件标记"],
        "acc": ["核心操作全量可追溯", "权限变更日志完整可查", "异常登录可识别并告警"],
        "weekly": "Backlog",
    },
    75: {
        "estimate": "4",
        "scope": ["VP看板指标：在线率、告警趋势、SLA、一次解决率", "日/周维度趋势", "指标口径说明"],
        "acc": ["四类核心指标可稳定展示", "看板支持日/周复盘视图", "指标口径文档可追溯"],
        "weekly": "Backlog",
    },
    76: {
        "estimate": "2",
        "scope": ["回滚预案、发布演练流程", "值班机制与应急联系人", "发布检查清单"],
        "acc": ["至少完成一次回滚演练", "发布清单可执行且可复用", "值班与应急机制明确并生效"],
        "weekly": "Backlog",
    },
}


def run(cmd):
    return subprocess.run(cmd, check=True, text=True, capture_output=True).stdout


def ensure_weekly_field():
    fields_raw = run(["gh", "project", "field-list", PROJECT_NUM, "--owner", OWNER, "--format", "json"])
    fields = json.loads(fields_raw)["fields"]
    for f in fields:
        if f["name"] == "周会状态":
            return f["id"], {o["name"]: o["id"] for o in f.get("options", [])}
    run(
        [
            "gh",
            "project",
            "field-create",
            PROJECT_NUM,
            "--owner",
            OWNER,
            "--name",
            "周会状态",
            "--data-type",
            "SINGLE_SELECT",
            "--single-select-options",
            "Backlog,本周进行中,待验收,已完成",
        ]
    )
    fields_raw = run(["gh", "project", "field-list", PROJECT_NUM, "--owner", OWNER, "--format", "json"])
    fields = json.loads(fields_raw)["fields"]
    for f in fields:
        if f["name"] == "周会状态":
            return f["id"], {o["name"]: o["id"] for o in f.get("options", [])}
    raise RuntimeError("未找到周会状态字段")


def get_project_id_and_status():
    data = json.loads(run(["gh", "project", "view", PROJECT_NUM, "--owner", OWNER, "--format", "json"]))
    project_id = data["id"]
    fields_raw = run(["gh", "project", "field-list", PROJECT_NUM, "--owner", OWNER, "--format", "json"])
    fields = json.loads(fields_raw)["fields"]
    status_field_id = ""
    status_opt = {}
    for f in fields:
        if f["name"] == "Status":
            status_field_id = f["id"]
            status_opt = {o["name"]: o["id"] for o in f.get("options", [])}
            break
    return project_id, status_field_id, status_opt


def get_item_map():
    items_raw = run(["gh", "project", "item-list", PROJECT_NUM, "--owner", OWNER, "--limit", "100", "--format", "json"])
    items = json.loads(items_raw)["items"]
    result = {}
    for it in items:
        content = it.get("content", {})
        number = content.get("number")
        if number:
            result[number] = it["id"]
    return result


def issue_body(meta):
    lines = [
        "## 目标",
        "完成 M0 管理后台核心能力交付，满足最小闭环要求。",
        "",
        "## 工作范围",
    ]
    lines.extend([f"- {x}" for x in meta["scope"]])
    lines.extend(["", "## 估算人日", f"**{meta['estimate']} 人日**", "", "## 验收标准"])
    lines.extend([f"- [ ] {x}" for x in meta["acc"]])
    lines.extend(
        [
            "",
            "## 交付物",
            "- 可运行的后端接口与必要配置",
            "- 基础测试与验收记录",
            "- 对应模块文档（接口/流程/规则）更新",
        ]
    )
    return "\n".join(lines)


def update_issue_bodies():
    for number, meta in ISSUES.items():
        with tempfile.NamedTemporaryFile("w", delete=False, suffix=".md", encoding="utf-8") as tmp:
            tmp.write(issue_body(meta))
            path = tmp.name
        subprocess.run(["gh", "issue", "edit", str(number), "-R", REPO, "--body-file", path], check=True)
        os.unlink(path)


def update_project_fields():
    project_id, status_field_id, status_opt = get_project_id_and_status()
    weekly_field_id, weekly_opt = ensure_weekly_field()
    item_map = get_item_map()
    for number, meta in ISSUES.items():
        item_id = item_map.get(number)
        if not item_id:
            continue
        weekly_option_id = weekly_opt[meta["weekly"]]
        subprocess.run(
            [
                "gh",
                "project",
                "item-edit",
                "--id",
                item_id,
                "--project-id",
                project_id,
                "--field-id",
                weekly_field_id,
                "--single-select-option-id",
                weekly_option_id,
            ],
            check=True,
        )
        status_name = "In Progress" if meta["weekly"] == "本周进行中" else "Todo"
        subprocess.run(
            [
                "gh",
                "project",
                "item-edit",
                "--id",
                item_id,
                "--project-id",
                project_id,
                "--field-id",
                status_field_id,
                "--single-select-option-id",
                status_opt[status_name],
            ],
            check=True,
        )


def main():
    update_issue_bodies()
    update_project_fields()
    print("Remote task management updated successfully.")


if __name__ == "__main__":
    main()
