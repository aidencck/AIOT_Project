import json
import subprocess

OWNER = "aidencck"
REPO = "aidencck/AIOT_Project"
PROJECT_NUMBER = "8"


def run(cmd):
    return subprocess.run(cmd, check=True, text=True, capture_output=True).stdout


def run_allow_fail(cmd):
    try:
        return run(cmd)
    except subprocess.CalledProcessError:
        return ""


def ensure_label(name, color, description):
    run_allow_fail(
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
        ]
    )


def get_milestone_map():
    raw = run(["gh", "api", f"repos/{REPO}/milestones", "--paginate"])
    milestones = json.loads(raw)
    return {m["title"]: m["number"] for m in milestones}


def ensure_milestone(title, description):
    mapping = get_milestone_map()
    if title in mapping:
        return mapping[title]
    raw = run(
        [
            "gh",
            "api",
            "-X",
            "POST",
            f"repos/{REPO}/milestones",
            "-f",
            f"title={title}",
            "-f",
            f"description={description}",
        ]
    )
    return json.loads(raw)["number"]


def create_issue(title, body, milestone_title, labels):
    cmd = ["gh", "issue", "create", "-R", REPO, "-t", title, "-b", body, "-m", milestone_title]
    if labels:
        cmd.extend(["-l", ",".join(labels)])
    out = run(cmd).strip()
    issue_url = out.splitlines()[-1]
    issue_number = int(issue_url.rstrip("/").split("/")[-1])
    return issue_number, issue_url


def get_project_ids():
    project = json.loads(run(["gh", "project", "view", PROJECT_NUMBER, "--owner", OWNER, "--format", "json"]))
    project_id = project["id"]
    fields = json.loads(run(["gh", "project", "field-list", PROJECT_NUMBER, "--owner", OWNER, "--format", "json"]))["fields"]
    status_field = next(f for f in fields if f["name"] == "Status")
    week_field = next(f for f in fields if f["name"] == "周会状态")
    status_options = {o["name"]: o["id"] for o in status_field["options"]}
    week_options = {o["name"]: o["id"] for o in week_field["options"]}
    return project_id, status_field["id"], week_field["id"], status_options, week_options


def add_issue_to_project(issue_url):
    run(["gh", "project", "item-add", PROJECT_NUMBER, "--owner", OWNER, "--url", issue_url])


def get_project_item_map():
    raw = run(["gh", "project", "item-list", PROJECT_NUMBER, "--owner", OWNER, "--limit", "200", "--format", "json"])
    items = json.loads(raw)["items"]
    result = {}
    for item in items:
        content = item.get("content", {})
        number = content.get("number")
        if number is not None:
            result[number] = item["id"]
    return result


def set_project_fields(item_id, project_id, status_field_id, week_field_id, status_option_id, week_option_id):
    run(
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
            status_option_id,
        ]
    )
    run(
        [
            "gh",
            "project",
            "item-edit",
            "--id",
            item_id,
            "--project-id",
            project_id,
            "--field-id",
            week_field_id,
            "--single-select-option-id",
            week_option_id,
        ]
    )


def main():
    ensure_label("track:admin-ui", "0366d6", "后台管理界面迭代")
    ensure_label("phase:1", "0e8a16", "阶段1 运营闭环强化")
    ensure_label("phase:2", "fbca04", "阶段2 效率与治理升级")
    ensure_label("phase:3", "d93f0b", "阶段3 数据驱动与智能化")
    ensure_label("priority:vp", "5319e7", "VP重点关注任务")

    phase1 = "后台管理迭代-阶段1（0-2个月）运营闭环强化"
    phase2 = "后台管理迭代-阶段2（3-5个月）效率与治理升级"
    phase3 = "后台管理迭代-阶段3（6-9个月）数据驱动与智能化"

    ensure_milestone(phase1, "先完成告警-工单-审计-看板闭环，支撑运营可执行。")
    ensure_milestone(phase2, "提升效率与治理能力，建立规则平台和运营工作台。")
    ensure_milestone(phase3, "进入智能化阶段，做预测、推荐与经营驾驶舱。")

    issues = [
        (
            "[Phase1] 告警中心增强（分级、流转、批量确认）",
            "目标：实现告警分级、状态流转、批量确认、误报标记。\n验收：告警可按级别处理并可追溯。",
            phase1,
            ["track:admin-ui", "phase:1", "priority:vp"],
        ),
        (
            "[Phase1] 工单中心闭环（自动建单、SLA、结案回写）",
            "目标：告警自动建单，支持分派、SLA倒计时、结案回写。\n验收：告警->工单转化率 >95%，P1响应率 >90%。",
            phase1,
            ["track:admin-ui", "phase:1", "priority:vp"],
        ),
        (
            "[Phase1] 审计中心V1（操作留痕、权限变更、导出）",
            "目标：关键操作审计、权限变更审计、导出能力。\n验收：关键操作审计覆盖率 100%。",
            phase1,
            ["track:admin-ui", "phase:1"],
        ),
        (
            "[Phase1] 经营看板V1（在线率、告警量、待处理工单）",
            "目标：提供核心运营指标与日常复盘入口。\n验收：日更成功率 100%，支持周会复盘。",
            phase1,
            ["track:admin-ui", "phase:1"],
        ),
        (
            "[Phase2] 规则平台升级（模板库、灰度、版本回滚）",
            "目标：规则模板化、版本化、灰度启停与回滚。\n验收：规则发布风险可控，支持回滚。",
            phase2,
            ["track:admin-ui", "phase:2", "priority:vp"],
        ),
        (
            "[Phase2] 运营工作台（待办+风险+目标联动）",
            "目标：按角色聚合运营待办与风险清单。\n验收：处理时长下降，跨角色协作效率提升。",
            phase2,
            ["track:admin-ui", "phase:2"],
        ),
        (
            "[Phase2] 权限治理升级（菜单+数据权限矩阵）",
            "目标：权限矩阵化与敏感操作二次确认。\n验收：越权风险显著下降并可审计。",
            phase2,
            ["track:admin-ui", "phase:2"],
        ),
        (
            "[Phase3] 智能预警与工单建议",
            "目标：故障趋势预测、工单处置建议与知识库推荐。\n验收：误报率下降20%，一次解决率提升15%。",
            phase3,
            ["track:admin-ui", "phase:3", "priority:vp"],
        ),
        (
            "[Phase3] VP经营驾驶舱（目标达成+风险热区+建议）",
            "目标：管理层一屏经营驾驶舱。\n验收：支持季度经营复盘与行动闭环。",
            phase3,
            ["track:admin-ui", "phase:3", "priority:vp"],
        ),
    ]

    created = []
    for title, body, milestone, labels in issues:
        number, url = create_issue(title, body, milestone, labels)
        created.append((number, url))
        add_issue_to_project(url)

    project_id, status_field_id, week_field_id, status_opts, week_opts = get_project_ids()
    item_map = get_project_item_map()
    for number, _ in created:
        item_id = item_map.get(number)
        if not item_id:
            continue
        set_project_fields(
            item_id,
            project_id,
            status_field_id,
            week_field_id,
            status_opts["Todo"],
            week_opts["Backlog"],
        )

    print("Created issues:", [n for n, _ in created])


if __name__ == "__main__":
    main()
