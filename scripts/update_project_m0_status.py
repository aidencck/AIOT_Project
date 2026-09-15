import json
import subprocess

OWNER = "aidencck"
PROJECT = "8"
REPO = "aidencck/AIOT_Project"
DONE = [68, 69, 70, 71, 72, 74, 75]
IN_PROGRESS = [73]


def run(cmd):
    return subprocess.check_output(cmd, text=True)


def main():
    project_id = json.loads(run(["gh", "project", "view", PROJECT, "--owner", OWNER, "--format", "json"]))["id"]
    fields = json.loads(run(["gh", "project", "field-list", PROJECT, "--owner", OWNER, "--format", "json"]))["fields"]
    status_field = next(f for f in fields if f["name"] == "Status")["id"]
    weekly_field = next(f for f in fields if f["name"] == "周会状态")["id"]

    items = json.loads(run(["gh", "project", "item-list", PROJECT, "--owner", OWNER, "--limit", "100", "--format", "json"]))["items"]
    item_by_num = {it["content"]["number"]: it["id"] for it in items if it.get("content", {}).get("number")}

    for number in DONE:
        item_id = item_by_num.get(number)
        if not item_id:
            continue
        subprocess.run(
            [
                "gh", "project", "item-edit",
                "--id", item_id,
                "--project-id", project_id,
                "--field-id", status_field,
                "--single-select-option-id", "98236657",
            ],
            check=True,
        )
        subprocess.run(
            [
                "gh", "project", "item-edit",
                "--id", item_id,
                "--project-id", project_id,
                "--field-id", weekly_field,
                "--single-select-option-id", "49fccaca",
            ],
            check=True,
        )

    for number in IN_PROGRESS:
        item_id = item_by_num.get(number)
        if not item_id:
            continue
        subprocess.run(
            [
                "gh", "project", "item-edit",
                "--id", item_id,
                "--project-id", project_id,
                "--field-id", status_field,
                "--single-select-option-id", "47fc9ee4",
            ],
            check=True,
        )
        subprocess.run(
            [
                "gh", "project", "item-edit",
                "--id", item_id,
                "--project-id", project_id,
                "--field-id", weekly_field,
                "--single-select-option-id", "a9ecc2da",
            ],
            check=True,
        )

    comment = (
        "最小闭环能力已落地：规则触发告警、告警自动建单、工单认领/完结、审计查询、经营看板。"
        "代码提交: fec1c9e（分支 feat/arch-core-task-board）。请验收。"
    )
    for number in DONE + IN_PROGRESS:
        subprocess.run(
            ["gh", "issue", "comment", str(number), "-R", REPO, "--body", comment],
            check=True,
            stdout=subprocess.DEVNULL,
        )


if __name__ == "__main__":
    main()
