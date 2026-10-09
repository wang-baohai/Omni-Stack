#!/usr/bin/env python3
"""
本地受控 Alertmanager webhook sink（在 Docker 中执行）。

职责：
  - 监听 127.0.0.1:59095，接收 POST /api/v1/receive-alert（Alertmanager webhook v4 JSON）。
  - 每条告警按白名单字段追加写 JSONL（timestamp/fingerprint/alertname/severity/status），
    绝不落 labels/annotations 原文，避免把可能含敏感值的字段写入证据文件。
  - 支持 --fail-window 模拟 receiver 短时失败（前 N 秒一律返回 500），
    用于验证 Alertmanager 的重试行为；窗口结束后恢复正常 200。

用法示例（通知验证）：
  python scripts/fixtures/observability/alert-receiver.py --out <external-evidence>/alerts.jsonl \
      --fail-window 30 --duration 600

安全约束：
  - 仅绑定 127.0.0.1，不对外网监听；不打印请求体、不打印任何 label 值。
  - 只使用 Python 标准库（http.server / json / argparse），无第三方依赖。
"""

import argparse
import json
import sys
import threading
import time
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

# 仅这些字段允许落入证据 JSONL（白名单），其余一律丢弃
_ALLOWED_KEYS = ("timestamp", "fingerprint", "alertname", "severity", "status")
_ALLOWED_STATUS = ("firing", "resolved")
# 请求体上限，防御异常大 payload
_MAX_BODY_BYTES = 2 * 1024 * 1024

# 运行期共享状态（由 main 填充）
_STATE = {
    "out_path": None,       # JSONL 输出路径
    "fail_until": 0.0,      # monotonic 时刻：此前一律回 500
    "write_lock": threading.Lock(),
    "counters": {"requests": 0, "alerts": 0, "rejected": 0, "failed_500": 0},
}


def _log(msg: str) -> None:
    """向 stderr 输出运行日志（不含任何告警内容/敏感字段）。"""
    print(f"[alert-receiver] {msg}", file=sys.stderr, flush=True)


def _extract_alerts(payload: dict) -> list:
    """
    从 Alertmanager v4 webhook JSON 中抽取白名单记录。

    @param payload - 已解析的请求体 dict（group 级）
    @return list[dict] - 每条含 timestamp/fingerprint/alertname/severity/status，
                        字段缺失时为 None；status 非 firing/resolved 的条目被丢弃
    """
    records = []
    now_iso = datetime.now(timezone.utc).isoformat()
    alerts = payload.get("alerts")
    if not isinstance(alerts, list):
        return records
    for item in alerts:
        if not isinstance(item, dict):
            continue
        status = item.get("status")
        if status not in _ALLOWED_STATUS:
            # 未知 status 不计入证据，避免污染送达判定
            _STATE["counters"]["rejected"] += 1
            continue
        labels = item.get("labels") if isinstance(item.get("labels"), dict) else {}
        record = {
            "timestamp": now_iso,
            "fingerprint": item.get("fingerprint"),
            "alertname": labels.get("alertname"),
            "severity": labels.get("severity"),
            "status": status,
        }
        records.append({k: record[k] for k in _ALLOWED_KEYS})
    return records


def _append_jsonl(records: list) -> None:
    """线程安全地把白名单记录追加写入 JSONL 证据文件。"""
    if not records:
        return
    with _STATE["write_lock"]:
        with open(_STATE["out_path"], "a", encoding="utf-8") as sink:
            for record in records:
                sink.write(json.dumps(record, ensure_ascii=False) + "\n")
            sink.flush()
        _STATE["counters"]["alerts"] += len(records)


class _Handler(BaseHTTPRequestHandler):
    """最小 webhook 端点：仅 POST /api/v1/receive-alert，其余 404/405。"""

    server_version = "OmniAlertSink/1.0"

    def _respond(self, code: int, body: dict) -> None:
        raw = json.dumps(body).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def do_POST(self) -> None:  # noqa: N802 (BaseHTTPRequestHandler 命名约定)
        """处理 POST：fail-window 内回 500；否则解析并落盘后回 200。"""
        _STATE["counters"]["requests"] += 1
        path = self.path.split("?", 1)[0]
        if path != "/api/v1/receive-alert":
            self._respond(404, {"ok": False, "error": "not-found"})
            return

        # 模拟 receiver 短时失败：窗口内不解析、不落盘，直接 500，触发 Alertmanager 重试
        if time.monotonic() < _STATE["fail_until"]:
            _STATE["counters"]["failed_500"] += 1
            # 仍需读掉 body，保持连接语义正常
            length = int(self.headers.get("Content-Length") or 0)
            if 0 < length <= _MAX_BODY_BYTES:
                self.rfile.read(length)
            self._respond(500, {"ok": False, "error": "simulated-failure"})
            return

        length = int(self.headers.get("Content-Length") or 0)
        if length <= 0 or length > _MAX_BODY_BYTES:
            self._respond(400, {"ok": False, "error": "bad-length"})
            return
        try:
            payload = json.loads(self.rfile.read(length).decode("utf-8"))
        except (ValueError, UnicodeDecodeError):
            self._respond(400, {"ok": False, "error": "bad-json"})
            return
        if not isinstance(payload, dict):
            self._respond(400, {"ok": False, "error": "bad-payload"})
            return

        _append_jsonl(_extract_alerts(payload))
        self._respond(200, {"ok": True})

    def do_GET(self) -> None:  # noqa: N802
        """健康检查端点，供 通知验证 确认 sink 已就绪（不含任何业务数据）。"""
        if self.path.split("?", 1)[0] == "/healthz":
            self._respond(200, {"ok": True})
        else:
            self._respond(404, {"ok": False, "error": "not-found"})

    def log_message(self, format: str, *args) -> None:  # 屏蔽默认 access log，避免记录 URL 参数
        """覆盖默认日志：只记一行方法+状态，不输出 body/labels。"""
        _log(f"{self.address_string()} {format % args}")


def main() -> int:
    """入口：解析参数、启动 HTTP 服务、可选定时退出。"""
    parser = argparse.ArgumentParser(description="本地受控 Alertmanager webhook sink（仅标准库）")
    parser.add_argument("--out", required=True, help="JSONL 证据输出路径（追加写）")
    parser.add_argument("--port", type=int, default=59095, help="监听端口，默认 59095（--host 控制绑定地址）")
    parser.add_argument("--host", default="127.0.0.1",
                        help="绑定地址；通知验证 容器化 sink 在隔离 compose 网络内用 0.0.0.0（不暴露宿主端口）")
    parser.add_argument("--fail-window", type=int, default=0,
                        help="启动后前 N 秒对所有 webhook 请求返回 500（模拟 receiver 故障），默认 0=不启用")
    parser.add_argument("--duration", type=int, default=0,
                        help="N 秒后自动退出并打印统计，默认 0=一直运行（Ctrl+C 退出）")
    args = parser.parse_args()

    _STATE["out_path"] = args.out
    _STATE["fail_until"] = time.monotonic() + args.fail_window if args.fail_window > 0 else 0.0

    # 预先触碰输出文件，尽早暴露路径问题
    with open(args.out, "a", encoding="utf-8"):
        pass

    server = ThreadingHTTPServer((args.host, args.port), _Handler)
    server.daemon_threads = True

    if args.fail_window > 0:
        _log(f"fail-window 生效：前 {args.fail_window}s 将返回 500")
    if args.duration > 0:
        timer = threading.Timer(args.duration, server.shutdown)
        timer.daemon = True
        timer.start()

    _log(f"监听 127.0.0.1:{args.port}，证据文件：{args.out}")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        _log("收到 Ctrl+C，退出")
    finally:
        server.server_close()
        counters = _STATE["counters"]
        _log(f"统计：requests={counters['requests']} alerts={counters['alerts']} "
             f"rejected={counters['rejected']} failed_500={counters['failed_500']}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
