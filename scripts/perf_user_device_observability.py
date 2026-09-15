#!/usr/bin/env python3
import argparse
import concurrent.futures
import dataclasses
import hashlib
import hmac
import json
import math
import os
import pathlib
import statistics
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request


ROOT_DIR = pathlib.Path(__file__).resolve().parents[1]
DEFAULT_ARTIFACT_DIR = ROOT_DIR / "artifacts" / "perf-user-device-observability"
SEED_SUMMARY_FILE = ROOT_DIR / "artifacts" / "service-communication" / "bootstrap" / "seed-summary.json"


@dataclasses.dataclass
class ScenarioConfig:
    scenario: str
    run_tag: str
    users: int
    devices_per_user: int
    seed_concurrency: int
    load_concurrency: int
    rounds: int
    base_gateway: str
    base_prom: str
    base_loki: str
    base_tempo: str
    webhook_secret: str
    product_key: str
    password: str
    artifact_dir: pathlib.Path
    mysql_password: str


@dataclasses.dataclass
class UserSeed:
    user_index: int
    phone: str
    token: str
    home_id: str
    device_ids: list[str]
    device_global_ids: list[str]


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


def retry_http_json(method: str, url: str, payload=None, headers=None, timeout: int = 30,
                    retry_statuses=None, attempts: int = 5, backoff_sec: float = 1.0):
    retry_statuses = retry_statuses or {403, 409, 429, 500, 502, 503, 504}
    last = None
    for attempt in range(1, attempts + 1):
        last = http_json(method, url, payload=payload, headers=headers, timeout=timeout)
        if last[0] not in retry_statuses:
            return last
        if attempt < attempts:
            time.sleep(backoff_sec * attempt)
    return last


def prom_query(base: str, expr: str):
    url = f"{base.rstrip('/')}/api/v1/query?{urllib.parse.urlencode({'query': expr})}"
    status, body, _ = http_json("GET", url, payload=None, headers={"Content-Type": "application/x-www-form-urlencoded"})
    if status != 200:
        raise RuntimeError(f"prom query failed: status={status}, expr={expr}, body={body[:400]}")
    data = json.loads(body)
    result = data.get("data", {}).get("result", [])
    if not result:
        return 0.0
    total = 0.0
    for item in result:
        value = item.get("value", [None, "0"])[1]
        total += float(value)
    return total


def loki_count(base: str, query: str):
    url = f"{base.rstrip('/')}/loki/api/v1/query?{urllib.parse.urlencode({'query': query})}"
    status, body, _ = http_json("GET", url, payload=None, headers={"Content-Type": "application/x-www-form-urlencoded"})
    if status != 200:
        raise RuntimeError(f"loki query failed: status={status}, body={body[:400]}")
    data = json.loads(body)
    result = data.get("data", {}).get("result", [])
    total = 0.0
    for item in result:
        value = item.get("value", [None, "0"])[1]
        total += float(value)
    return total


def tempo_recent_count(base: str, start_ns: int, service_name: str):
    url = f"{base.rstrip('/')}/api/search?{urllib.parse.urlencode({'tags': f'service.name={service_name}', 'limit': 20})}"
    status, body, _ = http_json("GET", url, payload=None, headers={"Content-Type": "application/x-www-form-urlencoded"})
    if status != 200:
        raise RuntimeError(f"tempo query failed: status={status}, body={body[:400]}")
    data = json.loads(body)
    count = 0
    for trace in data.get("traces", []):
        started = int(trace.get("startTimeUnixNano", "0"))
        if started >= start_ns:
            count += 1
    return count


def mysql_query(mysql_password: str, sql: str) -> str:
    command = [
        "docker", "exec", "aiot-mysql",
        "mysql",
        "-uaiot_app",
        f"-p{mysql_password}",
        "-Nse",
        sql,
        "aiot_cloud",
    ]
    result = subprocess.run(command, cwd=ROOT_DIR, check=True, capture_output=True, text=True)
    return result.stdout.strip()


def resolve_webhook_secret(cli_value: str) -> str:
    if cli_value:
        return cli_value
    env_value = os.environ.get("AIOT_EMQX_WEBHOOK_SECRET", "").strip()
    if env_value:
        return env_value
    inspect_cmd = (
        "docker inspect aiot-auth-service --format '{{range .Config.Env}}{{println .}}{{end}}' "
        "| grep '^AIOT_EMQX_WEBHOOK_SECRET=' | cut -d= -f2-"
    )
    result = subprocess.run(inspect_cmd, cwd=ROOT_DIR, shell=True, capture_output=True, text=True)
    secret = result.stdout.strip()
    if secret:
        return secret
    raise RuntimeError("unable to resolve AIOT_EMQX_WEBHOOK_SECRET from CLI, env, or aiot-auth-service container")


def percentile(sorted_values: list[float], p: float) -> float:
    if not sorted_values:
        return 0.0
    if len(sorted_values) == 1:
        return sorted_values[0]
    rank = math.ceil((p / 100.0) * len(sorted_values)) - 1
    rank = max(0, min(rank, len(sorted_values) - 1))
    return sorted_values[rank]


def phone_for_user(seed_mod: int, user_index: int) -> str:
    suffix = seed_mod * 1000 + user_index
    return f"139{suffix:08d}"


def normalize_tag(value: str, limit: int = 16) -> str:
    cleaned = "".join(ch for ch in value.lower() if ch.isalnum())
    if not cleaned:
        cleaned = "perf"
    return cleaned[:limit]


def build_device_identity(run_tag: str, user_index: int, device_index: int) -> tuple[str, str, str, str]:
    suffix = f"{run_tag}-u{user_index:04d}-d{device_index:02d}"
    device_name = f"perf-{suffix}"
    device_sn = f"sn-{suffix}"
    global_device_id = f"gdev-{suffix}"
    auth_identity = f"auth-{suffix}"
    return device_name, device_sn, global_device_id, auth_identity


def build_user_nickname(run_tag: str, user_index: int) -> str:
    return f"u{normalize_tag(run_tag, 12)}{user_index:04d}"


def _reuse_or_create_home(cfg: ScenarioConfig, token: str, home_url: str, user_index: int, home_name: str) -> str:
    # 跨轮重试幂等：用户可能已存在同名家庭（上一轮已建）。复用而非重建，
    # 否则设备绑定的 homeId 会漂移，导致 provision/exchange 命中「设备已绑定其他家庭」403。
    list_status, list_body, _ = http_json(
        "GET", home_url, headers={"Authorization": f"Bearer {token}"}, timeout=40
    )
    if list_status == 200:
        try:
            for item in json.loads(list_body).get("data") or []:
                if item.get("name") == home_name:
                    return item.get("id")
        except (ValueError, AttributeError):
            pass
    status, body, _ = retry_http_json(
        "POST", home_url,
        {"name": home_name, "location": "perf-lab"},
        headers={"Authorization": f"Bearer {token}"}, timeout=40,
    )
    if status not in (200, 201):
        raise RuntimeError(f"user {user_index} create home failed status={status} body={body[:300]}")
    return json.loads(body)["data"]


def register_login_create_home(cfg: ScenarioConfig, user_index: int, seed_mod: int) -> UserSeed:
    phone = phone_for_user(seed_mod, user_index)
    register_body = {
        "phone": phone,
        "password": cfg.password,
        "nickname": build_user_nickname(cfg.run_tag, user_index),
    }
    register_url = f"{cfg.base_gateway.rstrip('/')}/api/v1/users/register"
    login_url = f"{cfg.base_gateway.rstrip('/')}/api/v1/users/login"
    home_url = f"{cfg.base_gateway.rstrip('/')}/api/v1/homes"
    provision_token_url = f"{cfg.base_gateway.rstrip('/')}/api/v1/provision/token"
    provision_exchange_url = f"{cfg.base_gateway.rstrip('/')}/api/v1/provision/exchange"

    reg_status, reg_body, _ = retry_http_json("POST", register_url, register_body, timeout=40)
    # 400 = 「手机号已被注册」。UserServiceImpl 抛 BusinessException(VALIDATE_FAILED, "手机号已被注册")，
    # 但 GlobalExceptionHandler.handleBusinessException 在 resultCode != null 时走 Result.fail(resultCode)，
    # 丢弃自定义 message，仅返回 ResultCode 默认文案「参数检验失败」，故此处以 status=400 判定为「用户已存在」。
    # 种子阶段幂等处理：历史中断/时间窗口内 seed_mod 复用导致的重复手机号，直接复用既有账号走登录。
    if reg_status not in (200, 201, 409, 400):
        raise RuntimeError(f"user {user_index} register failed status={reg_status} body={reg_body[:300]}")

    login_status, login_body, _ = retry_http_json(
        "POST", login_url, {"phone": phone, "password": cfg.password}, timeout=40
    )
    if login_status != 200:
        raise RuntimeError(f"user {user_index} login failed status={login_status} body={login_body[:300]}")
    token = json.loads(login_body)["data"]["token"]

    home_id = _reuse_or_create_home(
        cfg, token, home_url, user_index, f"perf-home-{cfg.scenario}-{user_index:04d}"
    )

    device_ids = []
    device_global_ids = []
    for device_index in range(cfg.devices_per_user):
        device_name, device_sn, global_device_id, auth_identity = build_device_identity(
            cfg.run_tag, user_index, device_index
        )
        token_body = {
            "deviceName": device_name,
            "productKey": cfg.product_key,
            "homeId": home_id,
            "deviceSn": device_sn,
        }
        exchange_body = {
            "productKey": cfg.product_key,
            "deviceName": device_name,
            "deviceSn": device_sn,
            "globalDeviceId": global_device_id,
            "authIdentity": auth_identity,
            "provisionToken": None,
        }
        # 兑换配网 Token 是非幂等操作：ProvisionServiceImpl.provisionDevice 通过 getAndDelete
        # 一次性消费 token。若网关超时(503/504)但服务端已成功建号，用同一 token 重试会得到
        # 400「配网 Token 无效或已过期」（被 GlobalExceptionHandler 抹成「参数检验失败」）。
        # 因此每次重试都重新签发全新 token；设备建号本身幂等（DuplicateKeyException + findExistingDevices 兜底）。
        exchange_data = None
        for attempt in range(1, 6):
            status, body, _ = http_json(
                "POST",
                provision_token_url,
                token_body,
                headers={"Authorization": f"Bearer {token}"},
                timeout=40,
            )
            if status not in (200, 201):
                raise RuntimeError(
                    f"user {user_index} device {device_index} provision token failed status={status} body={body[:300]}"
                )
            exchange_body["provisionToken"] = json.loads(body)["data"]
            status, body, _ = http_json("POST", provision_exchange_url, exchange_body, timeout=40)
            if status in (200, 201):
                exchange_data = json.loads(body)["data"]
                break
            if status not in (500, 502, 503, 504):
                raise RuntimeError(
                    f"user {user_index} device {device_index} provision exchange failed status={status} body={body[:300]}"
                )
            time.sleep(1.0 * attempt)
        if exchange_data is None:
            raise RuntimeError(
                f"user {user_index} device {device_index} provision exchange failed after 5 attempts"
            )
        device_ids.append(exchange_data["deviceId"])
        device_global_ids.append(exchange_data["globalDeviceId"])

    return UserSeed(
        user_index=user_index,
        phone=phone,
        token=token,
        home_id=home_id,
        device_ids=device_ids,
        device_global_ids=device_global_ids,
    )


def sign_webhook(secret: str, action: str, client_id: str, username: str, timestamp_sec: int) -> str:
    payload = f"{action}.{client_id}.{username}.{timestamp_sec}".encode("utf-8")
    return hmac.new(secret.encode("utf-8"), payload, hashlib.sha256).hexdigest()


def webhook_once(cfg: ScenarioConfig, ordinal: int, username: str):
    action = "client.connected" if ordinal % 2 else "client.disconnected"
    client_id = f"perf-client-{cfg.scenario}-{ordinal:08d}"
    last = (0, "webhook retry not started", 0.0)
    for attempt in range(1, 6):
        ts = int(time.time())
        signature = sign_webhook(cfg.webhook_secret, action, client_id, username, ts)
        payload = {
            "action": action,
            "clientid": client_id,
            "username": username,
            "timestamp": ts,
        }
        headers = {
            "x-emqx-signature": signature,
            "X-Trace-Id": f"perf-{cfg.scenario}-{ordinal:08d}-a{attempt}",
        }
        last = http_json(
            "POST",
            f"{cfg.base_gateway.rstrip('/')}/api/v1/emqx/webhook",
            payload,
            headers=headers,
            timeout=30,
        )
        if last[0] not in {429, 500, 502, 503, 504}:
            return last
        if attempt < 5:
            time.sleep(0.2 * attempt)
    return last


def read_seed_product_key() -> str:
    if not SEED_SUMMARY_FILE.exists():
        raise FileNotFoundError(f"seed summary missing: {SEED_SUMMARY_FILE}")
    return json.loads(SEED_SUMMARY_FILE.read_text(encoding="utf-8"))["productKey"]


def product_exists(mysql_password: str, product_key: str) -> bool:
    if not product_key:
        return False
    sql = (
        "SELECT COUNT(*) FROM product_info "
        f"WHERE product_key = '{product_key}' AND is_deleted = 0"
    )
    return int(mysql_query(mysql_password, sql) or "0") > 0


def read_latest_product_key(mysql_password: str) -> str:
    sql = (
        "SELECT product_key FROM product_info "
        "WHERE is_deleted = 0 "
        "ORDER BY update_time DESC, create_time DESC LIMIT 1"
    )
    return mysql_query(mysql_password, sql)


def resolve_product_key(cli_value: str, mysql_password: str) -> str:
    if cli_value:
        return cli_value
    seed_key = ""
    try:
        seed_key = read_seed_product_key()
    except FileNotFoundError:
        seed_key = ""
    if seed_key and product_exists(mysql_password, seed_key):
        return seed_key
    latest_key = read_latest_product_key(mysql_password)
    if latest_key:
        return latest_key
    if seed_key:
        return seed_key
    raise RuntimeError("unable to resolve active product key from CLI, seed summary, or product_info")


def build_config(args) -> ScenarioConfig:
    artifact_dir = pathlib.Path(args.artifact_dir).resolve()
    artifact_dir.mkdir(parents=True, exist_ok=True)
    scenario = args.scenario or hex(int(time.time()))[-6:]
    run_tag = f"{normalize_tag(scenario, 10)}-{int(time.time()) % 100000:05d}"
    return ScenarioConfig(
        scenario=scenario,
        run_tag=run_tag,
        users=args.users,
        devices_per_user=args.devices_per_user,
        seed_concurrency=args.seed_concurrency,
        load_concurrency=args.load_concurrency,
        rounds=args.rounds,
        base_gateway=args.base_gateway,
        base_prom=args.base_prom,
        base_loki=args.base_loki,
        base_tempo=args.base_tempo,
        webhook_secret=resolve_webhook_secret(args.webhook_secret),
        product_key=resolve_product_key(args.product_key, args.mysql_password),
        password=args.password,
        artifact_dir=artifact_dir,
        mysql_password=args.mysql_password,
    )


def observe_metrics_total(cfg: ScenarioConfig) -> dict:
    return {
        "auth_webhook_requests": prom_query(
            cfg.base_prom,
            'sum(aiot_auth_webhook_request_total{service="aiot-auth-service"})',
        ),
        "gateway_post_requests": prom_query(
            cfg.base_prom,
            'sum(http_server_requests_seconds_count{service="aiot-gateway",method="POST"})',
        ),
        "rule_consume_success": prom_query(
            cfg.base_prom,
            'sum(aiot_stream_consume_success_total{service="aiot-rule-engine"})',
        ),
        "shadow_consume_success": prom_query(
            cfg.base_prom,
            'sum(aiot_stream_consume_success_total{service="aiot-shadow-service"})',
        ),
        "device_flush_success": prom_query(
            cfg.base_prom,
            'sum(aiot_device_status_flush_success_total)',
        ),
    }


def metric_deltas(current: dict, baseline: dict) -> dict:
    deltas = {}
    for key, value in current.items():
        deltas[key] = max(value - baseline.get(key, 0.0), 0.0)
    return deltas


def wait_for_prometheus_scrape(
    cfg: ScenarioConfig, baseline: dict[str, float], timeout_sec: int = 60, poll_sec: int = 5
) -> float:
    deadline = time.time() + timeout_sec
    flush = 0.0
    while time.time() <= deadline:
        current = observe_metrics_total(cfg)
        deltas = metric_deltas(current, baseline)
        flush = deltas["device_flush_success"]
        if (
            deltas["auth_webhook_requests"] > 0
            and deltas["rule_consume_success"] > 0
            and deltas["shadow_consume_success"] > 0
            and flush > 0
        ):
            return flush
        time.sleep(poll_sec)
    return flush


def query_device_status_counts(cfg: ScenarioConfig) -> tuple[int, int, int]:
    like_prefix = f"gdev-{cfg.run_tag}-%"
    mysql_line = mysql_query(
        cfg.mysql_password,
        "SELECT COUNT(*), "
        "SUM(CASE WHEN status = 1 THEN 1 ELSE 0 END), "
        "SUM(CASE WHEN status = 2 THEN 1 ELSE 0 END) "
        f"FROM device_info WHERE global_device_id LIKE '{like_prefix}' AND is_deleted = 0",
    )
    db_total, db_online, db_offline = [int(x or "0") for x in mysql_line.split("\t")]
    return db_total, db_online, db_offline


def wait_for_status_flush(cfg: ScenarioConfig, expected_devices: int, timeout_sec: int = 180, poll_sec: int = 5):
    deadline = time.time() + timeout_sec
    last = (0, 0, 0)
    while time.time() <= deadline:
        last = query_device_status_counts(cfg)
        if (last[1] + last[2]) >= expected_devices:
            return last
        time.sleep(poll_sec)
    return last


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--users", type=int, default=1000)
    parser.add_argument("--devices-per-user", type=int, default=10)
    parser.add_argument("--seed-concurrency", type=int, default=24)
    parser.add_argument("--load-concurrency", type=int, default=1000)
    parser.add_argument("--rounds", type=int, default=1)
    parser.add_argument("--base-gateway", default="http://127.0.0.1:8080")
    parser.add_argument("--base-prom", default="http://127.0.0.1:9090")
    parser.add_argument("--base-loki", default="http://127.0.0.1:3100")
    parser.add_argument("--base-tempo", default="http://127.0.0.1:3200")
    parser.add_argument("--webhook-secret", default="")
    parser.add_argument("--product-key", default="")
    parser.add_argument("--password", default="123456")
    parser.add_argument("--mysql-password", default=os.environ.get("MYSQL_PASSWORD", ""))
    parser.add_argument("--artifact-dir", default=str(DEFAULT_ARTIFACT_DIR))
    parser.add_argument("--scenario", default="")
    args = parser.parse_args()

    cfg = build_config(args)
    scenario_dir = cfg.artifact_dir / cfg.scenario
    scenario_dir.mkdir(parents=True, exist_ok=True)
    report_path = scenario_dir / "report.json"
    seed_path = scenario_dir / "seed.json"
    latency_path = scenario_dir / "latencies.json"

    started_ns = time.time_ns()
    seed_mod = int(time.time()) % 100000

    users: list[UserSeed] = []
    pending = list(range(cfg.users))
    max_seed_rounds = 3
    for rnd in range(max_seed_rounds):
        if not pending:
            break
        failed: list[int] = []
        with concurrent.futures.ThreadPoolExecutor(max_workers=cfg.seed_concurrency) as pool:
            future_to_idx = {pool.submit(register_login_create_home, cfg, idx, seed_mod): idx for idx in pending}
            for future in concurrent.futures.as_completed(future_to_idx):
                idx = future_to_idx[future]
                try:
                    users.append(future.result())
                except Exception as exc:  # noqa: BLE001
                    # 瞬时 503/网络抖动导致的单用户失败不应终止整个种子阶段：
                    # 记录失败用户，下一轮重试（register 已幂等：400→登录既有账号）。
                    failed.append(idx)
                    print(f"[seed] round={rnd} user={idx} failed: {exc}", file=sys.stderr)
        pending = failed
        if pending and rnd < max_seed_rounds - 1:
            time.sleep(10)
    if pending:
        raise RuntimeError(f"seed not complete after {max_seed_rounds} rounds; remaining={pending[:20]}...")
    users.sort(key=lambda item: item.user_index)
    seed_path.write_text(json.dumps([dataclasses.asdict(item) for item in users], ensure_ascii=True), encoding="utf-8")

    device_ids = []
    global_ids = []
    for user in users:
        device_ids.extend(user.device_ids)
        global_ids.extend(user.device_global_ids)
    device_ids = device_ids * max(1, cfg.rounds)

    prom_baseline = observe_metrics_total(cfg)

    load_started = time.time()
    results = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=cfg.load_concurrency) as pool:
        futures = [pool.submit(webhook_once, cfg, idx + 1, device_id) for idx, device_id in enumerate(device_ids)]
        for future in concurrent.futures.as_completed(futures):
            results.append(future.result())
    load_duration = max(time.time() - load_started, 0.001)

    db_total, db_online, db_offline = wait_for_status_flush(
        cfg, cfg.users * cfg.devices_per_user, timeout_sec=180, poll_sec=5
    )
    wait_for_prometheus_scrape(cfg, prom_baseline)
    prom_deltas = metric_deltas(observe_metrics_total(cfg), prom_baseline)
    latencies = sorted(item[2] for item in results)
    statuses = [item[0] for item in results]
    success_2xx = sum(1 for code in statuses if 200 <= code < 300)
    http_5xx = sum(1 for code in statuses if 500 <= code < 600)
    network_failed = sum(1 for code in statuses if code == 0)
    status_breakdown = {}
    for code in statuses:
        status_breakdown[str(code)] = status_breakdown.get(str(code), 0) + 1

    loki_hits = loki_count(
        cfg.base_loki,
        f'sum(count_over_time({{container=~"aiot-(auth-service|device-service|rule-engine|shadow-service)"}} |= "{cfg.run_tag}" [10m]))',
    )
    tempo_min_start_ns = started_ns - (5 * 60 * 1_000_000_000)
    tempo_gateway = tempo_recent_count(cfg.base_tempo, tempo_min_start_ns, "aiot-gateway")
    tempo_auth = tempo_recent_count(cfg.base_tempo, tempo_min_start_ns, "aiot-auth-service")

    report = {
        "scenario": cfg.scenario,
        "config": {
            "users": cfg.users,
            "devices_per_user": cfg.devices_per_user,
            "seed_concurrency": cfg.seed_concurrency,
            "load_concurrency": cfg.load_concurrency,
            "rounds": cfg.rounds,
            "product_key": cfg.product_key,
            "run_tag": cfg.run_tag,
        },
        "seed": {
            "users_created": len(users),
            "devices_created": len(global_ids),
        },
        "load": {
            "total_requests": len(results),
            "duration_sec": round(load_duration, 3),
            "throughput_rps": round(len(results) / load_duration, 3),
            "success_2xx": success_2xx,
            "http_5xx": http_5xx,
            "network_failed_000": network_failed,
            "success_ratio": round((success_2xx / len(results)) if results else 0.0, 6),
            "latency_avg_sec": round(statistics.mean(latencies) if latencies else 0.0, 6),
            "latency_p50_sec": round(percentile(latencies, 50), 6),
            "latency_p95_sec": round(percentile(latencies, 95), 6),
            "latency_p99_sec": round(percentile(latencies, 99), 6),
            "status_breakdown": status_breakdown,
        },
        "business_chain": {
            "db_devices_expected": cfg.users * cfg.devices_per_user,
            "db_devices_count": db_total,
            "db_online_count": db_online,
            "db_offline_count": db_offline,
        },
        "observability_chain": {
            "prometheus_deltas": {key: round(value, 3) for key, value in prom_deltas.items()},
            "loki_hits": loki_hits,
            "tempo_gateway_recent": tempo_gateway,
            "tempo_auth_recent": tempo_auth,
        },
    }
    report["checks"] = {
        "seed_users_match": report["seed"]["users_created"] == cfg.users,
        "seed_devices_match": report["seed"]["devices_created"] == cfg.users * cfg.devices_per_user,
        "load_success_ratio_ok": report["load"]["success_ratio"] >= 0.995,
        "load_no_5xx": report["load"]["http_5xx"] == 0,
        "load_no_network_fail": report["load"]["network_failed_000"] == 0,
        "db_devices_match": db_total == cfg.users * cfg.devices_per_user,
        "db_status_updated": (db_online + db_offline) > 0,
        "prom_auth_webhook_delta": prom_deltas["auth_webhook_requests"] > 0,
        "prom_rule_consume_delta": prom_deltas["rule_consume_success"] > 0,
        "prom_shadow_consume_delta": prom_deltas["shadow_consume_success"] > 0,
        "prom_device_flush_delta": prom_deltas["device_flush_success"] > 0,
        "loki_hits_present": loki_hits > 0,
        "tempo_hits_present": (tempo_gateway + tempo_auth) > 0,
    }
    report["status"] = "passed" if all(report["checks"].values()) else "failed"

    report_path.write_text(json.dumps(report, ensure_ascii=True, indent=2), encoding="utf-8")
    latency_path.write_text(json.dumps(latencies, ensure_ascii=True), encoding="utf-8")
    print(json.dumps(report, ensure_ascii=True, indent=2))
    return 0 if report["status"] == "passed" else 1


if __name__ == "__main__":
    raise SystemExit(main())
