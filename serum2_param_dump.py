#!/usr/bin/env python3
"""Dump all exposed Serum 2 parameters via OSC and store as JSON."""

from __future__ import annotations

import argparse
import json
import signal
import sys
import threading
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from pythonosc import dispatcher
from pythonosc import osc_server
from pythonosc import udp_client


@dataclass
class DumpState:
    device_name: str | None = None
    expected_count: int | None = None
    parameters: list[dict[str, Any]] | None = None
    error_message: str | None = None

    def __post_init__(self) -> None:
        if self.parameters is None:
            self.parameters = []


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Requests /device/allparams/list via OSC and writes the received parameter mapping as JSON."
        )
    )
    parser.add_argument("--host", default="127.0.0.1", help="OSC target host (Bitwig extension input host).")
    parser.add_argument("--port", type=int, required=True, help="OSC target port (Bitwig extension input port).")
    parser.add_argument(
        "--listen-host",
        default="0.0.0.0",
        help="Local host/IP to bind for OSC responses.",
    )
    parser.add_argument(
        "--listen-port",
        type=int,
        default=9001,
        help="Local UDP port to listen on for /device/allparams/* responses.",
    )
    parser.add_argument(
        "--output",
        default="serum2_mapping.json",
        help="Output JSON file path.",
    )
    parser.add_argument(
        "--timeout",
        type=float,
        default=5.0,
        help="Timeout in seconds while waiting for /device/allparams/end.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()

    state = DumpState()
    state_lock = threading.Lock()
    begin_event = threading.Event()
    end_event = threading.Event()
    stop_event = threading.Event()

    def handle_begin(address: str, *osc_args: Any) -> None:
        if len(osc_args) < 2:
            return

        with state_lock:
            state.device_name = str(osc_args[0])
            try:
                state.expected_count = int(osc_args[1])
            except (TypeError, ValueError):
                state.expected_count = None
        begin_event.set()

    def handle_item(address: str, *osc_args: Any) -> None:
        if len(osc_args) < 4:
            return

        try:
            item = {
                "index": int(osc_args[0]),
                "name": str(osc_args[1]),
                "display_value": str(osc_args[2]),
                "normalized_value": float(osc_args[3]),
            }
        except (TypeError, ValueError):
            return

        with state_lock:
            state.parameters.append(item)

    def handle_end(address: str, *osc_args: Any) -> None:
        end_event.set()

    def handle_error(address: str, *osc_args: Any) -> None:
        message = str(osc_args[0]) if osc_args else "unknown OSC error"
        with state_lock:
            state.error_message = message
        end_event.set()

    def stop_handler(signum: int, frame: Any) -> None:
        stop_event.set()
        end_event.set()

    signal.signal(signal.SIGINT, stop_handler)
    signal.signal(signal.SIGTERM, stop_handler)

    osc_dispatcher = dispatcher.Dispatcher()
    osc_dispatcher.map("/device/allparams/begin", handle_begin)
    osc_dispatcher.map("/device/allparams/item", handle_item)
    osc_dispatcher.map("/device/allparams/end", handle_end)
    osc_dispatcher.map("/device/allparams/error", handle_error)

    server = osc_server.ThreadingOSCUDPServer((args.listen_host, args.listen_port), osc_dispatcher)
    server_thread = threading.Thread(target=server.serve_forever, daemon=True)
    server_thread.start()

    client = udp_client.SimpleUDPClient(args.host, args.port)

    print(
        f"Listening on {args.listen_host}:{args.listen_port} and requesting list from {args.host}:{args.port}...",
        file=sys.stderr,
    )

    try:
        client.send_message("/device/allparams/list", [])

        completed_in_time = end_event.wait(timeout=args.timeout)
        if not completed_in_time:
            print(
                f"Timeout after {args.timeout:.1f}s while waiting for /device/allparams/end.",
                file=sys.stderr,
            )
            return 1

        if stop_event.is_set():
            print("Interrupted.", file=sys.stderr)
            return 130

        with state_lock:
            if state.error_message is not None:
                print(f"OSC error: {state.error_message}", file=sys.stderr)
                return 1

            output = {
                "plugin": "Serum 2",
                "timestamp": datetime.now(timezone.utc).isoformat(),
                "parameter_count": (
                    state.expected_count if state.expected_count is not None else len(state.parameters)
                ),
                "parameters": sorted(state.parameters, key=lambda entry: entry["index"]),
            }

            if state.device_name:
                output["device_name"] = state.device_name

            if begin_event.is_set() and state.expected_count is not None:
                received_count = len(state.parameters)
                if received_count != state.expected_count:
                    print(
                        (
                            "Warning: received item count "
                            f"({received_count}) differs from begin count ({state.expected_count})."
                        ),
                        file=sys.stderr,
                    )

        output_path = Path(args.output)
        output_path.parent.mkdir(parents=True, exist_ok=True)
        output_path.write_text(json.dumps(output, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

        print(f"Wrote mapping to {output_path}.", file=sys.stderr)
        return 0
    finally:
        server.shutdown()
        server.server_close()
        server_thread.join(timeout=1.0)


if __name__ == "__main__":
    raise SystemExit(main())
