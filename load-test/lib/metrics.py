"""Metrics collector + rolling-window stats.

Single-threaded (asyncio). All workers push OpResult to a shared asyncio.Queue;
one collector task drains the queue and updates the rolling window.

Design
------
- Rolling window: last WINDOW_SECONDS of observed results. We store each
  result's (ts, op_type, ok, latency_ms) and prune anything older than
  `now - WINDOW_SECONDS` before taking a snapshot. With 5-20s think time
  per op, even 128 concurrent users produce O(100) ops/s — a 30s window
  is O(3000) entries, which is cheap to sort for percentiles.
- Snapshot: what the reporter reads every ~5s. It's a copy of the current
  window's aggregates by op type. We return a plain dataclass so callers
  can print/log/store it without worrying about the collector mutating
  state under them.
- Failure classification follows the spec: ok=False from the worker
  counts as a failure, full stop. We don't re-classify by status code
  here — that's already done when OpResult was constructed in lib/ops.py.

Percentiles
-----------
`p50` / `p95` per op: sort the window's latencies and index. Cheap enough
for small windows and easier to reason about than streaming digests.
"""

from __future__ import annotations

import bisect
import time
from collections import deque
from dataclasses import dataclass, field
from typing import Iterable

from .ops import OpResult


WINDOW_SECONDS = 30.0


@dataclass
class OpWindowStats:
    op_type: str
    # HTTP method of this op type. Stable per op_type (we never have an op
    # that's sometimes GET and sometimes POST). Stored here so Snapshot can
    # compute per-method aggregates without the OpResult event trail.
    http_method: str = "GET"
    ok_count: int = 0
    fail_count: int = 0
    latencies_ms: list[float] = field(default_factory=list)  # successful only

    @property
    def total(self) -> int:
        return self.ok_count + self.fail_count

    @property
    def error_rate(self) -> float:
        t = self.total
        return 0.0 if t == 0 else self.fail_count / t

    def percentile(self, p: float) -> float:
        if not self.latencies_ms:
            return 0.0
        # latencies_ms is kept sorted on insert
        idx = int(len(self.latencies_ms) * p)
        if idx >= len(self.latencies_ms):
            idx = len(self.latencies_ms) - 1
        return self.latencies_ms[idx]


@dataclass
class MethodStats:
    """Aggregated stats for all ops of a given HTTP method (GET or POST)."""
    method: str
    total_ops: int = 0
    total_ok: int = 0
    total_fail: int = 0
    p50_ms: float = 0.0
    p95_ms: float = 0.0

    @property
    def error_rate(self) -> float:
        return 0.0 if self.total_ops == 0 else self.total_fail / self.total_ops


@dataclass
class Snapshot:
    """Aggregates across the current window. The reporter turns this into
    a one-line summary; Stage 2d's stop-condition evaluator reads the
    aggregate error rate + total op count.

    Per-method split: GET and POST have very different latency distributions
    because the server's POST read loop pins every upload at SO_TIMEOUT.
    Consumers should prefer per-method p50/p95 over aggregate for anything
    that claims to reflect real performance.
    """
    window_seconds: float
    total_ops: int
    total_ok: int
    total_fail: int
    per_op: dict[str, OpWindowStats] = field(default_factory=dict)
    by_method: dict[str, MethodStats] = field(default_factory=dict)

    @property
    def error_rate(self) -> float:
        return 0.0 if self.total_ops == 0 else self.total_fail / self.total_ops


class MetricsCollector:
    def __init__(self, window_seconds: float = WINDOW_SECONDS):
        self.window_seconds = window_seconds
        # deque of (ts_monotonic, OpResult). Pruned from the left on each
        # record or snapshot call. Bounded by ~(ops/s * WINDOW_SECONDS).
        self._events: deque[tuple[float, OpResult]] = deque()

    # ------------------------------------------------------------------
    # record/snapshot are called from the SAME asyncio task (the drainer);
    # worker tasks only put OpResults into the queue. So no locking needed.
    # ------------------------------------------------------------------

    def record(self, r: OpResult) -> None:
        self._events.append((time.monotonic(), r))

    def _prune(self, now: float) -> None:
        cutoff = now - self.window_seconds
        while self._events and self._events[0][0] < cutoff:
            self._events.popleft()

    def snapshot(self) -> Snapshot:
        now = time.monotonic()
        self._prune(now)

        per_op: dict[str, OpWindowStats] = {}
        total_ok = total_fail = 0
        for _ts, r in self._events:
            stats = per_op.get(r.op_type)
            if stats is None:
                stats = OpWindowStats(op_type=r.op_type, http_method=r.http_method)
                per_op[r.op_type] = stats
            if r.ok:
                stats.ok_count += 1
                total_ok += 1
                bisect.insort(stats.latencies_ms, r.latency_ms)
            else:
                stats.fail_count += 1
                total_fail += 1

        # Compute per-method aggregates. GET latency distribution reflects
        # real server processing; POST latency is pinned to SO_TIMEOUT and
        # shouldn't be mixed with GET when reporting p50/p95.
        by_method: dict[str, MethodStats] = {}
        for op_stats in per_op.values():
            m = op_stats.http_method
            ms = by_method.get(m)
            if ms is None:
                ms = MethodStats(method=m)
                by_method[m] = ms
            ms.total_ops += op_stats.total
            ms.total_ok += op_stats.ok_count
            ms.total_fail += op_stats.fail_count
        # Compute latency percentiles per method from the merged sorted list
        for m, ms in by_method.items():
            all_lat: list[float] = []
            for op_stats in per_op.values():
                if op_stats.http_method == m:
                    all_lat.extend(op_stats.latencies_ms)
            if all_lat:
                all_lat.sort()
                ms.p50_ms = all_lat[int(len(all_lat) * 0.50)]
                ms.p95_ms = all_lat[min(len(all_lat) - 1, int(len(all_lat) * 0.95))]

        return Snapshot(
            window_seconds=self.window_seconds,
            total_ops=total_ok + total_fail,
            total_ok=total_ok,
            total_fail=total_fail,
            per_op=per_op,
            by_method=by_method,
        )


# ---------- convenience ----------

def iter_snapshot_rows(snap: Snapshot) -> Iterable[tuple[str, int, int, float, float, float]]:
    """Yield one tuple per op type for tabular reporting:
    (op, total, fails, p50_ms, p95_ms, error_rate)"""
    for op in sorted(snap.per_op):
        s = snap.per_op[op]
        yield (op, s.total, s.fail_count, s.percentile(0.50), s.percentile(0.95), s.error_rate)


def aggregate_percentiles(snap: Snapshot) -> tuple[float, float]:
    """Overall p50 / p95 across all successful ops in the window, merged.

    Caution: this mixes POST (timeout-pinned) and GET latencies. For most
    reporting you probably want per-method split via `snap.by_method`.
    """
    all_lat: list[float] = []
    for s in snap.per_op.values():
        all_lat.extend(s.latencies_ms)
    if not all_lat:
        return 0.0, 0.0
    all_lat.sort()
    p50 = all_lat[int(len(all_lat) * 0.50)]
    p95 = all_lat[min(len(all_lat) - 1, int(len(all_lat) * 0.95))]
    return p50, p95


def method_percentiles(snap: Snapshot, method: str) -> tuple[float, float]:
    """p50 / p95 for a specific HTTP method ('GET' or 'POST')."""
    ms = snap.by_method.get(method)
    if ms is None:
        return 0.0, 0.0
    return ms.p50_ms, ms.p95_ms
