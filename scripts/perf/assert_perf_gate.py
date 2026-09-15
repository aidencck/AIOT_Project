#!/usr/bin/env python3
import json
import pathlib
import sys


THRESHOLDS = {
    "ci-smoke": {
        "min_success_ratio": 0.995,
        "max_http_5xx": 0,
        "max_network_failed": 0,
        "max_p95_sec": 0.200,
        "max_p99_sec": 0.400,
    },
    "nightly-baseline": {
        "min_success_ratio": 0.999,
        "max_http_5xx_ratio": 0.001,
        "max_network_failed": 0,
        "max_p95_sec": 0.300,
        "max_p99_sec": 0.800,
    },
}


def parse_report(path: pathlib.Path) -> dict:
    data = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        if "=" not in line:
            continue
        key, value = line.split("=", 1)
        data[key.strip()] = value.strip()
    return data


def main() -> int:
    if len(sys.argv) != 3:
        print("Usage: assert_perf_gate.py <report_file> <profile>", file=sys.stderr)
        return 2

    report_file = pathlib.Path(sys.argv[1])
    profile = sys.argv[2]
    if profile not in THRESHOLDS:
        print(f"Unknown profile: {profile}", file=sys.stderr)
        return 2

    report = parse_report(report_file)
    total_done = int(report.get("total_done", "0"))
    success_2xx = int(report.get("success_2xx", "0"))
    http_5xx = int(report.get("http_5xx", "0"))
    network_failed = int(report.get("network_failed_000", "0"))
    p95 = float(report.get("latency_p95_sec", "0"))
    p99 = float(report.get("latency_p99_sec", "0"))
    success_ratio = (success_2xx / total_done) if total_done else 0.0
    http_5xx_ratio = (http_5xx / total_done) if total_done else 1.0

    thresholds = THRESHOLDS[profile]
    failures = []

    if success_ratio < thresholds["min_success_ratio"]:
        failures.append(f"success_ratio={success_ratio:.4f} < {thresholds['min_success_ratio']:.4f}")
    if network_failed > thresholds["max_network_failed"]:
        failures.append(f"network_failed_000={network_failed} > {thresholds['max_network_failed']}")
    if p95 > thresholds["max_p95_sec"]:
        failures.append(f"latency_p95_sec={p95:.3f} > {thresholds['max_p95_sec']:.3f}")
    if p99 > thresholds["max_p99_sec"]:
        failures.append(f"latency_p99_sec={p99:.3f} > {thresholds['max_p99_sec']:.3f}")

    max_http_5xx = thresholds.get("max_http_5xx")
    if max_http_5xx is not None and http_5xx > max_http_5xx:
        failures.append(f"http_5xx={http_5xx} > {max_http_5xx}")

    max_http_5xx_ratio = thresholds.get("max_http_5xx_ratio")
    if max_http_5xx_ratio is not None and http_5xx_ratio > max_http_5xx_ratio:
        failures.append(f"http_5xx_ratio={http_5xx_ratio:.4f} > {max_http_5xx_ratio:.4f}")

    summary = {
        "profile": profile,
        "total_done": total_done,
        "success_ratio": round(success_ratio, 6),
        "http_5xx": http_5xx,
        "network_failed_000": network_failed,
        "latency_p95_sec": p95,
        "latency_p99_sec": p99,
        "status": "failed" if failures else "passed",
        "failures": failures,
    }
    print(json.dumps(summary, ensure_ascii=True, indent=2))

    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
