"""Live stdout reporter for Stage 2c.

Runs as its own asyncio task. Every REPORT_INTERVAL seconds, takes a
snapshot from the MetricsCollector and prints one line:

  t=  25s users= 8 ops=42.0/s GET p50= 15ms p95= 65ms | POST p50=520ms p95=540ms err= 0.0%

GET and POST latencies are split because the server's POST read loop
pins every upload at SO_TIMEOUT (see CLAUDE.md "POST Request Read Loop");
aggregated p95 would be misleading.

Per-op breakdown every N ticks (default every 6 ticks = 30s) to pin down
which op is contributing to latency/errors.
"""

from __future__ import annotations

import asyncio
import time
from typing import Callable, Optional

from .metrics import (
    MetricsCollector, Snapshot, iter_snapshot_rows,
    aggregate_percentiles, method_percentiles,
)


REPORT_INTERVAL_SECONDS = 5.0
PER_OP_BREAKDOWN_EVERY_N_TICKS = 6   # 6 * 5s = every 30s


def format_line(t_elapsed: float, users: int, snap: Snapshot) -> str:
    ops_per_s = snap.total_ops / snap.window_seconds if snap.window_seconds else 0.0
    get_p50, get_p95 = method_percentiles(snap, "GET")
    post_p50, post_p95 = method_percentiles(snap, "POST")
    err_pct = snap.error_rate * 100.0
    return (
        f"t={int(t_elapsed):4d}s "
        f"users={users:3d} "
        f"ops={ops_per_s:5.1f}/s "
        f"GET p50={int(get_p50):4d} p95={int(get_p95):4d}ms "
        f"| POST p50={int(post_p50):4d} p95={int(post_p95):4d}ms "
        f"err={err_pct:4.1f}%"
    )


def format_per_op_table(snap: Snapshot) -> str:
    if not snap.per_op:
        return ""
    lines = ["# per-op (window): op             total  fail   p50ms  p95ms  err%"]
    for op, total, fail, p50, p95, err_rate in iter_snapshot_rows(snap):
        lines.append(
            f"#                  {op:14s} {total:5d} {fail:5d} {int(p50):6d} {int(p95):6d} {err_rate*100:5.1f}"
        )
    return "\n".join(lines)


async def run_reporter(
    collector: MetricsCollector,
    get_user_count: Callable[[], int],
    stop_event: asyncio.Event,
    start_ts: Optional[float] = None,
    *,
    interval: float = REPORT_INTERVAL_SECONDS,
    show_per_op_every: int = PER_OP_BREAKDOWN_EVERY_N_TICKS,
    on_tick: Optional[Callable[[float, int, "Snapshot"], None]] = None,
) -> None:
    """Emit one summary line every `interval` seconds until stop_event is set.

    `get_user_count` is a callback so the live line reflects the CURRENT
    worker count (once Stage 2d ramps up, that number will change). For
    Stage 2c it's just len(workers).

    `on_tick` is an optional callback invoked with (t_elapsed, user_count,
    snapshot) once per tick BEFORE printing. Stage 2d uses this to feed
    the stop-condition evaluator and to capture TickRecord rows for the
    CSV report.
    """
    if start_ts is None:
        start_ts = time.monotonic()

    tick = 0
    try:
        while not stop_event.is_set():
            try:
                await asyncio.wait_for(stop_event.wait(), timeout=interval)
                break  # stop was set during the wait
            except asyncio.TimeoutError:
                pass  # interval elapsed, emit a line
            tick += 1
            elapsed = time.monotonic() - start_ts
            snap = collector.snapshot()
            user_count = get_user_count()
            if on_tick is not None:
                on_tick(elapsed, user_count, snap)
            print(format_line(elapsed, user_count, snap), flush=True)
            if show_per_op_every and tick % show_per_op_every == 0:
                breakdown = format_per_op_table(snap)
                if breakdown:
                    print(breakdown, flush=True)
    except asyncio.CancelledError:
        pass


async def run_drainer(
    queue: "asyncio.Queue",
    collector: MetricsCollector,
    stop_event: asyncio.Event,
) -> None:
    """Drain the ops queue into the collector. Runs until stop_event is set
    AND the queue is empty — so any in-flight results at shutdown are
    still recorded."""
    while True:
        if stop_event.is_set() and queue.empty():
            return
        try:
            item = await asyncio.wait_for(queue.get(), timeout=0.25)
        except asyncio.TimeoutError:
            continue
        collector.record(item)
        queue.task_done()
