#!/usr/bin/env python3
"""
观测开/关性能对照压测脚本（仅标准库，在 Docker 中执行）。

设计约束：
  - 只打有效业务端点（GET，需登录态），成功判定 = HTTP 2xx 且响应 JSON code==200；
    HTTP 200 但业务 code!=200 记为"业务失败"，绝不计入成功吞吐。
  - 线程池 + urllib 并发；--mode off|sample10 仅作为结果标签，不改变被测服务配置
    （采样率切换由 复核者 通过既定部署流程完成）。
  - token 只从 --auth-header-file 指向的文件读取（文件内容为 Authorization 头值），
    全程不打印、不落盘该值。
  - 每请求原始时延写 --raw-csv（毫秒，含 outcome 标签），便于 复核者 复核。

用法（复核者 直接照参数执行，两种 mode 各跑一次，再 compare）：
  # 1) off 基线（观测关闭）
  python scripts/fixtures/observability/perf-compare.py run \
      --base-url http://127.0.0.1:8102 --path /api/v1/messages/page?pageNum=1&pageSize=10 \
      --mode off --requests 3000 --concurrency 20 --warmup 100 --duration 120 \
      --auth-header-file <external-evidence>/auth-off.txt \
      --raw-csv <external-evidence>/raw-off.csv \
      --out <external-evidence>/perf-off.json

  # 2) sample10（观测开启、10% 采样，由 复核者 按部署流程切换后压测）
  ... 同上，--mode sample10、--auth-header-file auth-s10.txt ...

  # 3) 对照结论（预算：吞吐损失<=10%，P95/P99 增幅<=15%）
  python scripts/fixtures/observability/perf-compare.py compare \
      --baseline <external-evidence>/perf-off.json \
      --sampled  <external-evidence>/perf-sample10.json \
      --max-throughput-loss-pct 10 --max-latency-growth-pct 15 \
      --out <external-evidence>/perf-verdict.json

注意：--path 必须是真实业务读接口（要求响应 JSON 含数值 code 字段，code==200 为成功），
由 复核者 依据 F 环境实际网关路由填写；不要用健康检查冒充业务端点。
"""

import argparse
import csv
import json
import statistics
import sys
import threading
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone

# 业务成功判定：HTTP 2xx 且 JSON code == 200
_BUSINESS_OK_CODE = 200
# 单次请求体读取上限（防止异常大响应拖死统计），5 MB
_MAX_RESPONSE_BYTES = 5 * 1024 * 1024

# 每线程一个停止标志，由协调线程置位
_stop = threading.Event()
_results_lock = threading.Lock()
_results = []  # [(latency_ms, outcome)]


def _load_auth_value(path: str) -> str:
    """从文件读取 Authorization 头值（strip 换行）；绝不打印内容。"""
    with open(path, "r", encoding="utf-8") as handle:
        value = handle.read().strip()
    if not value:
        raise SystemExit("auth-header-file 为空：请写入完整 Authorization 头值（如 Bearer ...）")
    return value


def _one_request(base_url: str, path: str, auth_value: str, timeout: float):
    """
    执行一次 GET 并分类结果。

    @return (latency_ms, outcome) - outcome ∈
        success / http_error / business_error / timeout / connection_error / parse_error / status_error
    """
    url = base_url.rstrip("/") + path
    request = urllib.request.Request(url, method="GET",
                                     headers={"Authorization": auth_value,
                                              "Accept": "application/json"})
    started = time.perf_counter()
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            body = response.read(_MAX_RESPONSE_BYTES + 1)
            http_status = response.status
    except urllib.error.HTTPError as error:      # 非 2xx（HTTPError 含响应体）
        elapsed = (time.perf_counter() - started) * 1000.0
        return elapsed, "http_error"
    except urllib.error.URLError as error:
        elapsed = (time.perf_counter() - started) * 1000.0
        reason = getattr(error, "reason", None)
        if isinstance(reason, TimeoutError) or "timed out" in str(reason).lower():
            return elapsed, "timeout"
        return elapsed, "connection_error"
    except TimeoutError:
        return (time.perf_counter() - started) * 1000.0, "timeout"
    elapsed = (time.perf_counter() - started) * 1000.0

    if not (200 <= http_status < 300):
        return elapsed, "http_error"
    # 有效业务端点判定：响应 JSON 的 code 字段必须为 200
    try:
        payload = json.loads(body.decode("utf-8"))
        code = payload.get("code") if isinstance(payload, dict) else None
    except (ValueError, UnicodeDecodeError):
        return elapsed, "parse_error"
    if code == _BUSINESS_OK_CODE:
        return elapsed, "success"
    return elapsed, "business_error"


def _worker(base_url: str, path: str, auth_value: str, timeout: float) -> None:
    """压测工作线程：循环发请求直到 stop 或达到全局请求预算。"""
    while not _stop.is_set():
        latency_ms, outcome = _one_request(base_url, path, auth_value, timeout)
        with _results_lock:
            _results.append((latency_ms, outcome))


def _percentile(values: list, pct: float):
    """最近秩法分位数（对成功时延序列）；空序列返回 None。"""
    if not values:
        return None
    ordered = sorted(values)
    rank = max(1, int(round(pct / 100.0 * len(ordered))))
    return ordered[rank - 1]


def cmd_run(args: argparse.Namespace) -> int:
    """run 子命令：预热 + 采样窗口压测，写 raw-csv 与结果 JSON。"""
    auth_value = _load_auth_value(args.auth_header_file)
    base_url = args.base_url

    # ---- 预热：串行发 warmup 个请求，结果丢弃（排除连接/JIT/冷缓存噪声） ----
    for _ in range(args.warmup):
        _one_request(base_url, args.path, auth_value, args.timeout)

    # ---- 采样窗口：并发施压，双终止条件（请求数预算 或 时长上限） ----
    _results.clear()
    window_started = time.monotonic()
    with ThreadPoolExecutor(max_workers=args.concurrency) as pool:
        for _ in range(args.concurrency):
            pool.submit(_worker, base_url, args.path, auth_value, args.timeout)
        # 协调循环：到达 --requests 或 --duration 即请求停止（先达到者生效）
        while not _stop.is_set():
            time.sleep(0.05)
            with _results_lock:
                issued = len(_results)
            elapsed = time.monotonic() - window_started
            if issued >= args.requests or (args.duration > 0 and elapsed >= args.duration):
                _stop.set()
    window_elapsed = time.monotonic() - window_started

    # ---- 统计：失败绝不计入成功吞吐 ----
    latencies = [item[0] for item in _results]
    outcomes = [item[1] for item in _results]
    success = outcomes.count("success")
    breakdown = {
        "total": len(outcomes),
        "success": success,
        "http_error": outcomes.count("http_error"),
        "business_error": outcomes.count("business_error"),
        "timeout": outcomes.count("timeout"),
        "connection_error": outcomes.count("connection_error"),
        "parse_error": outcomes.count("parse_error"),
        "status_error": outcomes.count("status_error"),
    }
    breakdown["failed"] = breakdown["total"] - success
    success_latencies = [lat for lat, outcome in _results if outcome == "success"]
    result = {
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "mode": args.mode,
        "endpoint": {"baseUrl": base_url, "path": args.path},
        "config": {"requests": args.requests, "concurrency": args.concurrency,
                   "warmup": args.warmup, "durationLimitS": args.duration,
                   "timeoutS": args.timeout},
        "windowSeconds": round(window_elapsed, 3),
        "counts": breakdown,
        "throughputRps": round(success / window_elapsed, 3) if window_elapsed > 0 else None,
        "latencyMs": {
            "basis": "success-only",
            "avg": round(statistics.fmean(success_latencies), 3) if success_latencies else None,
            "p50": _round_or_none(_percentile(success_latencies, 50)),
            "p95": _round_or_none(_percentile(success_latencies, 95)),
            "p99": _round_or_none(_percentile(success_latencies, 99)),
        },
        "rawLatencyMsAllRequests": {
            "avg": round(statistics.fmean(latencies), 3) if latencies else None,
        },
    }

    with open(args.raw_csv, "w", newline="", encoding="utf-8") as handle:
        writer = csv.writer(handle)
        writer.writerow(["latency_ms", "outcome"])
        writer.writerows(_results)

    with open(args.out, "w", encoding="utf-8") as handle:
        json.dump(result, handle, ensure_ascii=False, indent=2)

    # 输出摘要不含任何凭据
    print(json.dumps({"mode": args.mode, "counts": breakdown,
                      "throughputRps": result["throughputRps"],
                      "latencyMs": result["latencyMs"], "out": args.out},
                     ensure_ascii=False, indent=2))
    if breakdown["failed"] > 0:
        print("警告：存在失败请求，成功吞吐仅按 success 计。", file=sys.stderr)
    return 0


def _round_or_none(value):
    return round(value, 3) if value is not None else None


def cmd_compare(args: argparse.Namespace) -> int:
    """compare 子命令：off vs sample10 差异计算与预算判定。"""
    with open(args.baseline, "r", encoding="utf-8") as handle:
        base = json.load(handle)
    with open(args.sampled, "r", encoding="utf-8") as handle:
        samp = json.load(handle)
    if base.get("mode") != "off" or samp.get("mode") != "sample10":
        raise SystemExit(f"mode 标签不符：baseline={base.get('mode')} sampled={samp.get('mode')}")

    def _loss(base_val, samp_val, higher_is_better: bool):
        """相对变化百分比；base 为 0/None 时返回 None。"""
        if not base_val or not samp_val:
            return None
        delta = (base_val - samp_val) if higher_is_better else (samp_val - base_val)
        return round(delta / base_val * 100.0, 3)

    base_tp = base.get("throughputRps")
    samp_tp = samp.get("throughputRps")
    throughput_loss_pct = _loss(base_tp, samp_tp, higher_is_better=True)
    checks = {
        "throughputLossPct": {
            "value": throughput_loss_pct,
            "budgetMaxPct": args.max_throughput_loss_pct,
            "pass": throughput_loss_pct is not None
            and throughput_loss_pct <= args.max_throughput_loss_pct,
        },
    }
    for key in ("p95", "p99"):
        base_val = base["latencyMs"].get(key)
        samp_val = samp["latencyMs"].get(key)
        growth_pct = _loss(base_val, samp_val, higher_is_better=False)
        checks[f"latencyGrowth_{key}"] = {
            "baseMs": base_val, "sampledMs": samp_val, "growthPct": growth_pct,
            "budgetMaxPct": args.max_latency_growth_pct,
            "pass": growth_pct is not None
            and growth_pct <= args.max_latency_growth_pct,
        }
    # 失败率护栏：任一 mode 存在非 success 请求即整体判 FAIL（复核者 需先排除环境噪声）
    checks["zeroFailedRequests"] = {
        "baselineFailed": base["counts"]["failed"],
        "sampledFailed": samp["counts"]["failed"],
        "pass": base["counts"]["failed"] == 0 and samp["counts"]["failed"] == 0,
    }
    overall_pass = all(item["pass"] for item in checks.values())
    verdict = {
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "budget": {"maxThroughputLossPct": args.max_throughput_loss_pct,
                   "maxLatencyGrowthPct": args.max_latency_growth_pct},
        "baselineSummary": {"throughputRps": base_tp, "latencyMs": base["latencyMs"],
                            "counts": base["counts"]},
        "sampledSummary": {"throughputRps": samp_tp, "latencyMs": samp["latencyMs"],
                           "counts": samp["counts"]},
        "checks": checks,
        "overallPass": overall_pass,
    }
    with open(args.out, "w", encoding="utf-8") as handle:
        json.dump(verdict, handle, ensure_ascii=False, indent=2)
    print(json.dumps(verdict, ensure_ascii=False, indent=2))
    return 0 if overall_pass else 1


def main() -> int:
    """入口：run / compare 两个子命令。"""
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[2])
    sub = parser.add_subparsers(dest="command", required=True)

    run = sub.add_parser("run", help="对单一 mode 压测并输出统计")
    run.add_argument("--base-url", required=True, help="F 网关基址，如 http://127.0.0.1:19902")
    run.add_argument("--path", required=True,
                     help="业务端点路径（响应 JSON code==200 才算成功），如 /api/v1/... ")
    run.add_argument("--mode", required=True, choices=["off", "sample10"], help="结果标签")
    run.add_argument("--requests", type=int, default=2000, help="窗口内请求数预算")
    run.add_argument("--concurrency", type=int, default=20, help="并发线程数")
    run.add_argument("--warmup", type=int, default=100, help="预热请求数（不计入统计）")
    run.add_argument("--duration", type=int, default=0,
                     help="窗口时长上限秒数；与 --requests 先达到者生效；0=仅按请求数")
    run.add_argument("--timeout", type=float, default=10.0, help="单请求超时秒")
    run.add_argument("--auth-header-file", required=True,
                     help="含 Authorization 头值的文件（不打印内容）")
    run.add_argument("--raw-csv", required=True, help="每请求原始时延 CSV 输出路径")
    run.add_argument("--out", required=True, help="本 mode 统计 JSON 输出路径")
    run.set_defaults(func=cmd_run)

    cmp_ = sub.add_parser("compare", help="off vs sample10 预算判定")
    cmp_.add_argument("--baseline", required=True, help="perf-off.json")
    cmp_.add_argument("--sampled", required=True, help="perf-sample10.json")
    cmp_.add_argument("--max-throughput-loss-pct", type=float, default=10.0)
    cmp_.add_argument("--max-latency-growth-pct", type=float, default=15.0)
    cmp_.add_argument("--out", required=True, help="结论 JSON 输出路径")
    cmp_.set_defaults(func=cmd_compare)

    args = parser.parse_args()
    return args.func(args)


if __name__ == "__main__":
    sys.exit(main())
