#!/usr/bin/env python3
"""AIoT 全维度压测矩阵。

覆盖维度（均可独立运行或 --matrix all 全跑）：
  infra         基础设施盘点：8 服务健康/延迟、MySQL 表与数据分布、Redis Stream 积压、Ollama 模型
  http          经网关读路径压测：home/room/member/device/product/shadow
  ingest        数据上报链路压测：mqtt-adapter -> data-parser -> Redis Stream
  ai            AI 诊断链路压测：rule-engine /api/v1/ai/diagnosis
  observability 观测链路：Prometheus 指标 / Loki 日志 / Tempo trace

仅依赖 Python 标准库，读路径/基础设施用 urllib（无 keep-alive）；ai 维度用 http.client 持久连接（keep-alive）以便与 ab 可比。
"""
import argparse
import concurrent.futures
import http.client
import json
import math
import os
import pathlib
import statistics
import subprocess
import threading
import time
import urllib.error
import urllib.parse
import urllib.request

ROOT_DIR = pathlib.Path(__file__).resolve().parents[1]
DEFAULT_ARTIFACT_DIR = ROOT_DIR / "artifacts" / "perf-full-matrix"

SERVICES = [
    ("aiot-gateway", 8080),
    ("aiot-device-service", 8081),
    ("aiot-auth-service", 8082),
    ("aiot-home-service", 8083),
    ("aiot-rule-engine", 8084),
    ("aiot-mqtt-adapter", 8085),
    ("aiot-data-parser", 8086),
    ("aiot-shadow-service", 8087),
]


def http_json(method: str, url: str, payload=None, headers=None, timeout: int = 30):
    data = None
    final_headers = {"Content-Type": "application/json"}
    if headers:
        final_headers.update(headers)
    if payload is not None:
        data = json.dumps(payload, ensure_ascii=True).encode("utf-8")
    req = urllib.request.Request(url=url, data=data, headers=final_headers, method=method)
    started = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            body = resp.read().decode("utf-8")
            return resp.status, body, time.perf_counter() - started
    except urllib.error.HTTPError as exc:
        body = exc.read().decode("utf-8")
        return exc.code, body, time.perf_counter() - started
    except Exception as exc:  # noqa: BLE001
        return 0, str(exc), time.perf_counter() - started


def percentile(sorted_values: list[float], p: float) -> float:
    if not sorted_values:
        return 0.0
    if len(sorted_values) == 1:
        return sorted_values[0]
    rank = math.ceil((p / 100.0) * len(sorted_values)) - 1
    rank = max(0, min(rank, len(sorted_values) - 1))
    return sorted_values[rank]


def mysql_query(database: str, sql: str, mysql_password: str) -> str:
    command = [
        "docker", "exec", "aiot-mysql", "mysql",
        "-uaiot_app", f"-p{mysql_password}", "-Nse", sql, database,
    ]
    result = subprocess.run(command, cwd=ROOT_DIR, check=True, capture_output=True, text=True)
    return result.stdout.strip()


def redis_cli(*args: str) -> str:
    command = ["docker", "exec", "aiot-redis", "redis-cli", *args]
    result = subprocess.run(command, cwd=ROOT_DIR, check=True, capture_output=True, text=True)
    return result.stdout.strip()


def redis_stream_field(stream_key: str, field: str) -> int:
    """解析 XINFO STREAM 输出中的数值字段（如 entries-added），不受 MAXLEN trim 影响。"""
    lines = redis_cli("XINFO", "STREAM", stream_key).splitlines()
    for i, line in enumerate(lines):
        if line.strip() == field and i + 1 < len(lines):
            try:
                return int(lines[i + 1].strip())
            except ValueError:
                return 0
    return 0


def prom_query(base: str, expr: str) -> float:
    url = f"{base.rstrip('/')}/api/v1/query?{urllib.parse.urlencode({'query': expr})}"
    status, body, _ = http_json("GET", url, headers={"Content-Type": "application/x-www-form-urlencoded"})
    if status != 200:
        return 0.0
    data = json.loads(body)
    result = data.get("data", {}).get("result", [])
    if not result:
        return 0.0
    total = 0.0
    for item in result:
        value = item.get("value", [None, "0"])[1]
        try:
            total += float(value)
        except (TypeError, ValueError):
            pass
    return total


def loki_count(base: str, query: str) -> float:
    url = f"{base.rstrip('/')}/loki/api/v1/query?{urllib.parse.urlencode({'query': query})}"
    status, body, _ = http_json("GET", url, headers={"Content-Type": "application/x-www-form-urlencoded"})
    if status != 200:
        return 0.0
    data = json.loads(body)
    result = data.get("data", {}).get("result", [])
    total = 0.0
    for item in result:
        value = item.get("value", [None, "0"])[1]
        try:
            total += float(value)
        except (TypeError, ValueError):
            pass
    return total


def tempo_recent_count(base: str, service_name: str, limit: int = 20) -> int:
    url = f"{base.rstrip('/')}/api/search?{urllib.parse.urlencode({'tags': f'service.name={service_name}', 'limit': limit})}"
    status, body, _ = http_json("GET", url, headers={"Content-Type": "application/x-www-form-urlencoded"})
    if status != 200:
        return 0
    try:
        return len(json.loads(body).get("traces", []))
    except ValueError:
        return 0


def run_load(worker, args_list, concurrency: int) -> list[tuple[int, float]]:
    results: list[tuple[int, float]] = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=concurrency) as pool:
        futures = [pool.submit(worker, arg) for arg in args_list]
        for future in concurrent.futures.as_completed(futures):
            try:
                results.append(future.result())
            except Exception:  # noqa: BLE001
                results.append((0, 0.0))
    return results


def summarize(results: list[tuple[int, float]], duration_sec: float) -> dict:
    latencies = sorted(r[1] for r in results)
    statuses = [r[0] for r in results]
    total = len(results)
    success_2xx = sum(1 for c in statuses if 200 <= c < 300)
    http_4xx = sum(1 for c in statuses if 400 <= c < 500)
    http_5xx = sum(1 for c in statuses if 500 <= c < 600)
    network_failed = sum(1 for c in statuses if c == 0)
    breakdown: dict[str, int] = {}
    for c in statuses:
        breakdown[str(c)] = breakdown.get(str(c), 0) + 1
    return {
        "total_requests": total,
        "duration_sec": round(duration_sec, 3),
        "throughput_rps": round(total / duration_sec, 3) if duration_sec else 0.0,
        "success_2xx": success_2xx,
        "http_4xx": http_4xx,
        "http_5xx": http_5xx,
        "network_failed_000": network_failed,
        "success_ratio": round(success_2xx / total, 6) if total else 0.0,
        "latency_avg_sec": round(statistics.mean(latencies), 6) if latencies else 0.0,
        "latency_p50_sec": round(percentile(latencies, 50), 6),
        "latency_p95_sec": round(percentile(latencies, 95), 6),
        "latency_p99_sec": round(percentile(latencies, 99), 6),
        "status_breakdown": breakdown,
    }


def resolve_internal_token(cli_value: str) -> str:
    if cli_value:
        return cli_value
    env_value = os.environ.get("AIOT_INTERNAL_TOKEN", "").strip()
    if env_value:
        return env_value
    inspect_cmd = (
        "docker inspect aiot-auth-service --format '{{range .Config.Env}}{{println .}}{{end}}' "
        "| grep '^AIOT_INTERNAL_TOKEN=' | cut -d= -f2-"
    )
    result = subprocess.run(inspect_cmd, cwd=ROOT_DIR, shell=True, capture_output=True, text=True)
    token = result.stdout.strip()
    if token:
        return token
    raise RuntimeError("unable to resolve AIOT_INTERNAL_TOKEN from CLI, env, or aiot-auth-service container")


def resolve_test_account(mysql_password: str, cli_phone: str, cli_home: str, cli_device: str) -> dict:
    """从库里解析一个「有设备的 home + owner 手机号 + 设备 id」，供读路径压测使用。"""
    if cli_phone and cli_home and cli_device:
        return {"phone": cli_phone, "home_id": cli_home, "device_id": cli_device}

    home_id = cli_home or mysql_query(
        "aiot_cloud",
        "SELECT home_id FROM device_info WHERE home_id IS NOT NULL AND home_id<>'' AND is_deleted=0 LIMIT 1",
        mysql_password,
    )
    if not home_id:
        raise RuntimeError("no seeded home/device found; run fix429 seed first or pass --phone/--home-id/--device-id")

    device_id = cli_device or mysql_query(
        "aiot_cloud",
        f"SELECT id FROM device_info WHERE home_id='{home_id}' AND is_deleted=0 LIMIT 1",
        mysql_password,
    )
    phone = cli_phone or mysql_query(
        "aiot_home",
        "SELECT u.phone FROM home_member hm JOIN user_info u ON u.id=hm.user_id "
        f"WHERE hm.home_id='{home_id}' AND hm.role=1 LIMIT 1",
        mysql_password,
    )
    return {"phone": phone, "home_id": home_id, "device_id": device_id}


def login(base_gateway: str, phone: str, password: str) -> str:
    url = f"{base_gateway.rstrip('/')}/api/v1/users/login"
    status, body, _ = http_json("POST", url, {"phone": phone, "password": password}, timeout=40)
    if status != 200:
        raise RuntimeError(f"login failed status={status} body={body[:300]}")
    token = json.loads(body)["data"]["token"]
    return token


# --------------------------------------------------------------------------- infra
def collect_infra(base_gateway: str, mysql_password: str) -> dict:
    # 1) 8 服务健康 + 直连延迟
    services = {}
    for name, port in SERVICES:
        url = f"http://127.0.0.1:{port}/actuator/health"
        latencies: list[float] = []
        statuses: list[int] = []
        for _ in range(30):
            status, _, elapsed = http_json("GET", url, timeout=5)
            statuses.append(status)
            latencies.append(elapsed)
        services[name] = {
            "port": port,
            "health_status": max(set(statuses), key=statuses.count),
            "health_ok_ratio": round(sum(1 for c in statuses if c == 200) / len(statuses), 4),
            "latency_ms": {
                "p50": round(percentile(sorted(latencies), 50) * 1000, 3),
                "p95": round(percentile(sorted(latencies), 95) * 1000, 3),
                "p99": round(percentile(sorted(latencies), 99) * 1000, 3),
            },
        }

    # 2) MySQL 表清单与数据分布
    mysql = {}
    for db in ("aiot_cloud", "aiot_home"):
        tables_raw = mysql_query(db, "SHOW TABLES", mysql_password)
        tables = [t for t in tables_raw.splitlines() if t.strip()]
        row_counts = {}
        for table in tables:
            try:
                cnt = mysql_query(db, f"SELECT COUNT(*) FROM `{table}`", mysql_password)
                row_counts[table] = int(cnt or "0")
            except Exception:  # noqa: BLE001
                row_counts[table] = -1
        mysql[db] = {"table_count": len(tables), "tables": tables, "row_counts": row_counts}

    device_dist = {}
    try:
        dist_raw = mysql_query(
            "aiot_cloud",
            "SELECT COUNT(*), COALESCE(SUM(status=0),0), COALESCE(SUM(status=1),0), COALESCE(SUM(status=2),0) "
            "FROM device_info WHERE is_deleted=0",
            mysql_password,
        )
        parts = [int(x or "0") for x in dist_raw.split("\t")]
        total = parts[0]
        cov_raw = mysql_query(
            "aiot_cloud",
            "SELECT COALESCE(SUM(room_id IS NOT NULL AND room_id<>''),0), "
            "COALESCE(SUM(gateway_id IS NOT NULL AND gateway_id<>''),0), "
            "COALESCE(SUM(firmware_version IS NOT NULL AND firmware_version<>''),0) "
            "FROM device_info WHERE is_deleted=0",
            mysql_password,
        )
        cov = [int(x or "0") for x in cov_raw.split("\t")]
        device_dist = {
            "total": total,
            "status_0_unactivated": parts[1],
            "status_1_online": parts[2],
            "status_2_offline": parts[3],
            "room_id_coverage": round(cov[0] / total, 6) if total else 0.0,
            "gateway_id_coverage": round(cov[1] / total, 6) if total else 0.0,
            "firmware_version_coverage": round(cov[2] / total, 6) if total else 0.0,
        }
    except Exception as exc:  # noqa: BLE001
        device_dist = {"error": str(exc)}

    # 3) Redis Stream 积压
    redis = {}
    try:
        redis["stream_device_event_len"] = int(redis_cli("XLEN", "aiot:stream:device-event") or "0")
        redis["stream_device_event_dlq_len"] = int(redis_cli("XLEN", "aiot:stream:device-event:dlq") or "0")
        groups = redis_cli("XINFO", "GROUPS", "aiot:stream:device-event")
        parsed_groups: list[dict] = []
        current: dict | None = None
        lines = [l.strip() for l in groups.splitlines() if l.strip()]
        for i in range(0, len(lines), 2):
            key = lines[i]
            value = lines[i + 1] if i + 1 < len(lines) else ""
            if key == "name":
                if current:
                    parsed_groups.append(current)
                current = {"name": value}
            elif current is not None:
                current[key] = value
        if current:
            parsed_groups.append(current)
        redis["consumer_groups"] = parsed_groups
    except Exception as exc:  # noqa: BLE001
        redis["error"] = str(exc)

    # 4) Ollama 模型
    ollama = {}
    try:
        status, body, _ = http_json("GET", "http://127.0.0.1:11434/api/tags", timeout=10)
        if status == 200:
            ollama["models"] = [m.get("name") for m in json.loads(body).get("models", [])]
        else:
            ollama["status"] = status
    except Exception as exc:  # noqa: BLE001
        ollama["error"] = str(exc)

    return {"services": services, "mysql": mysql, "device_info_distribution": device_dist, "redis": redis, "ollama": ollama}


# --------------------------------------------------------------------------- http read
def collect_http_read(base_gateway: str, token: str, account: dict, concurrency: int, requests: int) -> dict:
    home_id = account["home_id"]
    device_id = account["device_id"]
    auth_headers = {"Authorization": f"Bearer {token}"}
    endpoints = [
        ("GET", "/api/v1/homes", None),
        ("GET", f"/api/v1/rooms?homeId={home_id}", None),
        ("GET", f"/api/v1/homes/{home_id}/members", None),
        ("GET", f"/api/v1/devices?homeId={home_id}", None),
        ("GET", f"/api/v1/devices/page?homeId={home_id}&pageNo=1&pageSize=20", None),
        ("GET", "/api/v1/products", None),
        ("GET", f"/api/v1/devices/{device_id}/shadow", None),
    ]
    matrix = []
    for method, path, _ in endpoints:
        url = f"{base_gateway.rstrip('/')}{path}"

        def worker(_arg, method=method, url=url):
            status, _, elapsed = http_json(method, url, headers=auth_headers, timeout=30)
            return status, elapsed

        started = time.time()
        results = run_load(worker, list(range(requests)), concurrency)
        summary = summarize(results, time.time() - started)
        matrix.append({"method": method, "path": path, **summary})
    return matrix


# --------------------------------------------------------------------------- ingest
def collect_ingest(internal_token: str, concurrency: int, requests: int) -> dict:
    before_entries = redis_stream_field("aiot:stream:device-event", "entries-added")
    url = "http://127.0.0.1:8085/api/v1/mqtt/messages"
    headers = {"X-Internal-Token": internal_token}

    def worker(idx: int):
        payload = "online" if idx % 2 == 0 else "offline"
        body = {
            "messageId": f"perf-matrix-{idx:08d}",
            "deviceId": f"gdev-perf-matrix-{idx:08d}",
            "topic": "dev/telemetry",
            "payload": payload,
            "timestamp": int(time.time() * 1000),
        }
        status, _, elapsed = http_json("POST", url, body, headers=headers, timeout=30)
        return status, elapsed

    started = time.time()
    results = run_load(worker, list(range(requests)), concurrency)
    summary = summarize(results, time.time() - started)
    time.sleep(2)
    after_entries = redis_stream_field("aiot:stream:device-event", "entries-added")
    summary["stream_entries_added_before"] = before_entries
    summary["stream_entries_added_after"] = after_entries
    summary["stream_entries_added_delta"] = after_entries - before_entries
    return summary


# --------------------------------------------------------------------------- ai
def collect_ai(base_gateway: str, token: str, account: dict, concurrency: int, requests: int,
               mysql_password: str = "") -> dict:
    device_id = account["device_id"]
    parsed = urllib.parse.urlsplit(base_gateway)
    host = parsed.hostname or "127.0.0.1"
    port = parsed.port or (443 if parsed.scheme == "https" else 80)
    base_path = parsed.path.rstrip("/")
    path = (base_path if base_path else "") + "/api/v1/ai/diagnosis"
    headers = {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}
    scenes = ["OFFLINE_FLAP", "PROVISION_FAILURE", "SHADOW_DIFF"]
    run_tag = time.strftime("%Y%m%d%H%M%S")
    local = threading.local()

    def get_conn():
        conn = getattr(local, "conn", None)
        if conn is None:
            conn = http.client.HTTPConnection(host, port, timeout=60)
            local.conn = conn
        return conn

    def worker(idx: int):
        body = {
            "deviceId": device_id,
            "sceneType": scenes[idx % len(scenes)],
            "eventId": f"perf-ai-{run_tag}-{idx:08d}",
        }
        data = json.dumps(body, ensure_ascii=True).encode("utf-8")
        started = time.perf_counter()
        try:
            conn = get_conn()
            conn.request("POST", path, body=data, headers=headers)
            resp = conn.getresponse()
            resp.read()
            return resp.status, time.perf_counter() - started
        except Exception:  # noqa: BLE001
            conn = getattr(local, "conn", None)
            if conn is not None:
                try:
                    conn.close()
                except Exception:  # noqa: BLE001
                    pass
                local.conn = None
            return 0, time.perf_counter() - started

    started = time.time()
    results = run_load(worker, list(range(requests)), concurrency)
    summary = summarize(results, time.time() - started)

    summary["source_distribution"] = {}
    if mysql_password:
        try:
            src_raw = mysql_query(
                "aiot_cloud",
                "SELECT IFNULL(JSON_UNQUOTE(JSON_EXTRACT(diagnosis_result,'$.source')),'null') src, COUNT(*) "
                "FROM ai_diagnosis_record GROUP BY src",
                mysql_password,
            )
            for line in src_raw.splitlines():
                if not line.strip():
                    continue
                parts = line.split("\t")
                if len(parts) == 2:
                    summary["source_distribution"][parts[0]] = int(parts[1] or "0")
        except Exception as exc:  # noqa: BLE001
            summary["source_distribution"] = {"error": str(exc)}
    return summary


# --------------------------------------------------------------------------- observability
def collect_observability(base_prom: str, base_loki: str, base_tempo: str) -> dict:
    prom = {
        "up_targets": prom_query(base_prom, "count(up == 1)"),
        "stream_backlog_length": prom_query(base_prom, "aiot_stream_backlog_length"),
        "auth_webhook_request_total": prom_query(base_prom, 'sum(aiot_auth_webhook_request_total)'),
        "stream_consume_success_total": prom_query(base_prom, 'sum(aiot_stream_consume_success_total)'),
        "device_status_flush_success_total": prom_query(base_prom, 'sum(aiot_device_status_flush_success_total)'),
        "gateway_http_requests_total": prom_query(
            base_prom, 'sum(http_server_requests_seconds_count{service="aiot-gateway"})'
        ),
    }
    loki = {
        "gateway": loki_count(base_loki, 'sum(count_over_time({service="aiot-gateway"} [5m]))'),
        "auth": loki_count(base_loki, 'sum(count_over_time({service="aiot-auth-service"} [5m]))'),
        "device": loki_count(base_loki, 'sum(count_over_time({service="aiot-device-service"} [5m]))'),
        "rule_engine": loki_count(base_loki, 'sum(count_over_time({service="aiot-rule-engine"} [5m]))'),
        "shadow": loki_count(base_loki, 'sum(count_over_time({service="aiot-shadow-service"} [5m]))'),
        "mqtt_adapter": loki_count(base_loki, 'sum(count_over_time({service="aiot-mqtt-adapter"} [5m]))'),
        "data_parser": loki_count(base_loki, 'sum(count_over_time({service="aiot-data-parser"} [5m]))'),
    }
    tempo = {name: tempo_recent_count(base_tempo, name) for name, _ in SERVICES}
    return {"prometheus": prom, "loki_log_lines_5m": loki, "tempo_traces": tempo}


# --------------------------------------------------------------------------- main
def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--matrix", default="all", help="comma list: all|infra|http|ingest|ai|observability")
    parser.add_argument("--base-gateway", default="http://127.0.0.1:8080")
    parser.add_argument("--base-prom", default="http://127.0.0.1:9090")
    parser.add_argument("--base-loki", default="http://127.0.0.1:3100")
    parser.add_argument("--base-tempo", default="http://127.0.0.1:3200")
    parser.add_argument("--mysql-password", default=os.environ.get("MYSQL_PASSWORD", ""))
    parser.add_argument("--internal-token", default="")
    parser.add_argument("--phone", default="")
    parser.add_argument("--home-id", default="")
    parser.add_argument("--device-id", default="")
    parser.add_argument("--password", default="123456")
    parser.add_argument("--concurrency", type=int, default=50)
    parser.add_argument("--requests", type=int, default=200)
    parser.add_argument("--ingest-concurrency", type=int, default=200)
    parser.add_argument("--ingest-requests", type=int, default=2000)
    parser.add_argument("--ai-concurrency", type=int, default=10)
    parser.add_argument("--ai-requests", type=int, default=100)
    parser.add_argument("--artifact-dir", default=str(DEFAULT_ARTIFACT_DIR))
    parser.add_argument("--scenario", default="")
    args = parser.parse_args()

    matrices = set(m.strip() for m in args.matrix.split(","))
    if "all" in matrices:
        matrices = {"infra", "http", "ingest", "ai", "observability"}

    scenario = args.scenario or time.strftime("%Y%m%d-%H%M%S")
    artifact_dir = pathlib.Path(args.artifact_dir).resolve() / scenario
    artifact_dir.mkdir(parents=True, exist_ok=True)

    report = {
        "scenario": scenario,
        "generated_at": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
        "matrix": sorted(matrices),
        "config": {
            "base_gateway": args.base_gateway,
            "concurrency": args.concurrency,
            "requests": args.requests,
            "ingest_concurrency": args.ingest_concurrency,
            "ingest_requests": args.ingest_requests,
            "ai_concurrency": args.ai_concurrency,
            "ai_requests": args.ai_requests,
        },
    }

    account = None
    token = None

    if "infra" in matrices:
        print("[matrix] infra ...", flush=True)
        report["infrastructure"] = collect_infra(args.base_gateway, args.mysql_password)

    if "http" in matrices or "ai" in matrices:
        account = resolve_test_account(args.mysql_password, args.phone, args.home_id, args.device_id)
        token = login(args.base_gateway, account["phone"], args.password)
        report["test_account"] = {
            "phone": account["phone"],
            "home_id": account["home_id"],
            "device_id": account["device_id"],
        }

    if "http" in matrices:
        print("[matrix] http read ...", flush=True)
        report["http_read_matrix"] = collect_http_read(
            args.base_gateway, token, account, args.concurrency, args.requests
        )

    if "ingest" in matrices:
        print("[matrix] ingest ...", flush=True)
        report["data_ingest_chain"] = collect_ingest(
            resolve_internal_token(args.internal_token), args.ingest_concurrency, args.ingest_requests
        )

    if "ai" in matrices:
        print("[matrix] ai ...", flush=True)
        report["ai_diagnosis_chain"] = collect_ai(
            args.base_gateway, token, account, args.ai_concurrency, args.ai_requests, args.mysql_password
        )

    if "observability" in matrices:
        print("[matrix] observability ...", flush=True)
        report["observability"] = collect_observability(args.base_prom, args.base_loki, args.base_tempo)

    # 汇总检查（软断言，不阻断：全维度压测重在采集，硬门禁由 perf 门禁脚本负责）
    checks = {}
    infra = report.get("infrastructure", {})
    services = infra.get("services", {})
    checks["all_services_healthy"] = all(s.get("health_status") == 200 for s in services.values()) if services else None
    http_matrix = report.get("http_read_matrix", [])
    checks["http_all_2xx"] = all(m.get("http_5xx", 0) == 0 and m.get("network_failed_000", 0) == 0 for m in http_matrix) if http_matrix else None
    ingest = report.get("data_ingest_chain", {})
    checks["ingest_stream_written"] = (ingest.get("stream_entries_added_delta", 0) > 0) if ingest else None
    report["checks"] = checks

    report_path = artifact_dir / "report.json"
    report_path.write_text(json.dumps(report, ensure_ascii=True, indent=2), encoding="utf-8")
    print(json.dumps(report, ensure_ascii=True, indent=2))
    print(f"\nreport written: {report_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
