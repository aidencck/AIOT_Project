#!/usr/bin/env python3
"""
AIoT 拓扑生成器：从 scripts/lib/services.json（SSOT）派生/校验各端业务服务端口副本。

职责边界：
  - 业务服务端口（8 个 Java 服务）：由本生成器派生到 compose / k8s / 文档，禁止人手二次硬编码。
  - infra 端口（mysql/redis/emqx/nacos/ollama）：由 scripts/lib/common.sh 的 service::guard 校验，
    aiotctl 运行时通过 infra::port 读取，不在此生成。

用法：
  python3 scripts/gen_topology.py            # 修正所有端口副本（写入）
  python3 scripts/gen_topology.py --check    # 仅校验，漂移则退出码 1（CI 门禁）
"""
import argparse
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SSOT_PATH = os.path.join(ROOT, "scripts", "lib", "services.json")

# k8s services.<key> 命名（去 aiot- 前缀 + 驼峰）
K8S_KEYS = {
    "aiot-gateway": "gateway",
    "aiot-device-service": "deviceService",
    "aiot-auth-service": "authService",
    "aiot-home-service": "homeService",
    "aiot-rule-engine": "ruleEngine",
    "aiot-shadow-service": "shadowService",
    "aiot-mqtt-adapter": "mqttAdapter",
    "aiot-data-parser": "dataParser",
}


def load_ssot():
    with open(SSOT_PATH, encoding="utf-8") as f:
        return json.load(f)


def service_map(ssot):
    return {s["name"]: s for s in ssot["services"]}


def _scan_blocks(text, indent=2):
    """逐行返回 (行号, 顶层缩进键, 块文本)。键以 indent 空格缩进且下一行更深缩进视为块起点。"""
    lines = text.split("\n")
    for i, line in enumerate(lines):
        stripped = line.lstrip(" ")
        level = len(line) - len(stripped)
        if level == indent and stripped and not stripped.startswith("-") and not stripped.startswith("#"):
            if stripped.endswith(":"):
                yield i, stripped[:-1], lines
            elif stripped and not stripped.endswith(":"):
                # 无冒号的缩进键（如 compose 的裸 key），同样视作锚点
                yield i, stripped, lines


def _replace_in_block(lines, start_idx, key, regex, repl):
    """在从 start_idx 开始的块内（到下一个 indent==2 行为止）应用 regex 替换。返回 (新行列表, 替换次数, 错误)。"""
    n = 0
    err = ""
    # 定位块结束：下一个缩进<=2 且非空行
    end = len(lines)
    for j in range(start_idx + 1, len(lines)):
        s = lines[j].lstrip(" ")
        lvl = len(lines[j]) - len(s)
        if s and lvl <= 2:
            end = j
            break
    found = False
    for j in range(start_idx, end):
        new, c = regex.subn(repl, lines[j])
        if c:
            found = True
            n += c
            lines[j] = new
    if not found:
        err = f"块内未匹配到目标行: {key}"
    return lines, n, err


def fix_compose_server_port(text, svc_map):
    """compose 全量定义（docker-compose.yml / compose.base.yml）：在服务块内修正 SERVER_PORT。"""
    lines = text.split("\n")
    changed = False
    errors = []
    for name, svc in svc_map.items():
        port = svc["port"]
        target = None
        for i, line in enumerate(lines):
            s = line.lstrip(" ")
            lvl = len(line) - len(s)
            if lvl == 2 and s == name + ":":
                target = i
                break
        if target is None:
            errors.append(f"compose: 未找到服务块 {name}")
            continue
        rx = re.compile(r"(SERVER_PORT:\s*)\d+")
        for j in range(target, len(lines)):
            s = lines[j].lstrip(" ")
            lvl = len(lines[j]) - len(s)
            if j > target and s and lvl <= 2:
                break
            new, c = rx.subn(r"\g<1>%d" % port, lines[j])
            if c:
                if new != lines[j]:
                    changed = True
                lines[j] = new
    return "\n".join(lines), changed, errors


def fix_k8s_port(text, svc_map):
    """k8s values.yaml：在 services.<key> 块内修正 port（整数，区别于 endpoints 的带引号端口）。"""
    lines = text.split("\n")
    changed = False
    errors = []
    for name, svc in svc_map.items():
        key = K8S_KEYS[name]
        port = svc["port"]
        target = None
        for i, line in enumerate(lines):
            s = line.lstrip(" ")
            lvl = len(line) - len(s)
            if lvl == 2 and s == key + ":":
                target = i
                break
        if target is None:
            errors.append(f"k8s: 未找到服务块 {key}")
            continue
        rx = re.compile(r"(\bport:\s*)\d+")
        matched = False
        for j in range(target, len(lines)):
            s = lines[j].lstrip(" ")
            lvl = len(lines[j]) - len(s)
            if j > target and s and lvl <= 2:
                break
            new, c = rx.subn(r"\g<1>%d" % port, lines[j])
            if c:
                matched = True
                if new != lines[j]:
                    changed = True
                lines[j] = new
        if not matched:
            errors.append(f"k8s: 服务块 {key} 内未找到 port")
    return "\n".join(lines), changed, errors


def fix_overlay_ports(text, svc_map, env_conf=None, host_ports=None):
    """compose overlay（dev/staging/prod/ci）：修正业务服务 ports 行 host:container。"""
    lines = text.split("\n")
    changed = False
    errors = []
    # 本 overlay 需要暴露的业务服务集合
    if host_ports:
        expose = set(host_ports.keys()) & set(svc_map.keys())
    else:
        expose = set((env_conf or {}).get("expose", [])) & set(svc_map.keys())
    for name in expose:
        port = svc_map[name]["port"]
        host = port
        if host_ports and name in host_ports:
            host = host_ports[name]
        target = None
        for i, line in enumerate(lines):
            s = line.lstrip(" ")
            lvl = len(line) - len(s)
            if lvl == 2 and s == name + ":":
                target = i
                break
        if target is None:
            errors.append(f"overlay: 未找到服务块 {name}")
            continue
        rx = re.compile(r'(\s+-\s+")\d+:\d+(")')
        matched = False
        for j in range(target, len(lines)):
            s = lines[j].lstrip(" ")
            lvl = len(lines[j]) - len(s)
            if j > target and s and lvl <= 2:
                break
            new, c = rx.subn(lambda m: f'{m.group(1)}{host}:{port}{m.group(2)}', lines[j])
            if c:
                matched = True
                if new != lines[j]:
                    changed = True
                lines[j] = new
        if not matched:
            errors.append(f"overlay: 服务块 {name} 内未找到 ports")
    return "\n".join(lines), changed, errors


def _render_table(svc_map, compact):
    """渲染 2 列端口表（左 4 服务，右 4 服务）。compact=True 紧凑，False 对齐。"""
    names = list(svc_map.keys())
    left = names[:4]
    right = names[4:]
    if compact:
        header = "| 服务 | 端口 | 服务 | 端口 |"
        sep = "|---|---|---|---|"
        rows = [
            f"| {left[i]} | {svc_map[left[i]]['port']} | {right[i]} | {svc_map[right[i]]['port']} |"
            for i in range(4)
        ]
    else:
        header = "| 服务                  | 端口   | 服务                  | 端口   |"
        sep = "| ------------------- | ---- | ------------------- | ---- |"
        rows = [
            f"| {left[i]:<20} | {svc_map[left[i]]['port']:<5} | {right[i]:<20} | {svc_map[right[i]]['port']:<5} |"
            for i in range(4)
        ]
    return [header, sep] + rows


def fix_markdown_table(text, svc_map, compact):
    """文档端口表：重写 AIOT-GEN 标记区内的表格。"""
    begin = re.compile(r"<!--\s*AIOT-GEN:\s*port-table\s*-->")
    end = re.compile(r"<!--\s*AIOT-GEN-END:\s*port-table\s*-->")
    lines = text.split("\n")
    b = e = None
    for i, line in enumerate(lines):
        if begin.search(line):
            b = i
        if end.search(line):
            e = i
            break
    if b is None or e is None or b >= e:
        return text, False, ["markdown: 未找到 AIOT-GEN port-table 标记区"]
    new_block = _render_table(svc_map, compact)
    new_lines = lines[:b + 1] + new_block + lines[e:]
    return "\n".join(new_lines), "\n".join(new_lines) != text, []


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true", help="仅校验，漂移则退出码 1")
    args = ap.parse_args()

    ssot = load_ssot()
    svc_map = service_map(ssot)
    envs = ssot["environments"]

    targets = [
        # (文件路径, 修正函数, 参数)
        (os.path.join(ROOT, "docker-compose.yml"), fix_compose_server_port, {}),
        (os.path.join(ROOT, "compose", "compose.base.yml"), fix_compose_server_port, {}),
        (os.path.join(ROOT, "k8s", "helm", "values.yaml"), fix_k8s_port, {}),
        (os.path.join(ROOT, "compose", "compose.dev.yml"), fix_overlay_ports, {"env_conf": envs["dev"]}),
        (os.path.join(ROOT, "compose", "compose.staging.yml"), fix_overlay_ports, {"env_conf": envs["staging"]}),
        (os.path.join(ROOT, "compose", "compose.prod.yml"), fix_overlay_ports, {"env_conf": envs["prod"]}),
        (os.path.join(ROOT, "docker-compose.ci.yml"), fix_overlay_ports, {"host_ports": envs["ci"]["host_ports"]}),
        (os.path.join(ROOT, ".trae", "checklists", "RELEASE_ROLLBACK_LOOP.md"), fix_markdown_table, {"compact": False}),
    ]
    # 本机 Skill 文档（CI 沙箱不存在则跳过）
    skill = os.path.expanduser("~/.trae/skills/aiot-remote-dev/SKILL.md")
    if os.path.exists(skill):
        targets.append((skill, fix_markdown_table, {"compact": True}))

    all_errors = []
    drifted = False
    for path, fn, kw in targets:
        if not os.path.exists(path):
            all_errors.append(f"文件不存在: {path}")
            continue
        with open(path, encoding="utf-8") as f:
            text = f.read()
        new_text, changed, errors = fn(text, svc_map, **kw)
        all_errors.extend([f"{os.path.relpath(path, ROOT)}: {e}" for e in errors])
        if changed:
            drifted = True
            if args.check:
                print(f"[DRIFT] {os.path.relpath(path, ROOT)}")
            else:
                with open(path, "w", encoding="utf-8") as f:
                    f.write(new_text)
                print(f"[FIXED] {os.path.relpath(path, ROOT)}")

    if all_errors:
        print("生成器错误：", file=sys.stderr)
        for e in all_errors:
            print(f"  - {e}", file=sys.stderr)
        sys.exit(2)

    if args.check and drifted:
        print("拓扑漂移：存在端口副本与 SSOT 不一致，请运行 `python3 scripts/gen_topology.py` 修正。", file=sys.stderr)
        sys.exit(1)

    if args.check:
        print("topology OK: 所有端口副本与 SSOT 一致")
    else:
        print("topology 已同步（无漂移）" if not drifted else "topology 已修正")


if __name__ == "__main__":
    main()
