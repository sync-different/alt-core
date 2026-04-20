#!/usr/bin/env python3
"""Phase 2 — Stages 2b/2c/2d: fixed-level load OR doubling ramp.

Modes (selected by --ramp / --no-ramp):

  --no-ramp (default for --users N): spawn N users immediately, hold for
      --duration seconds. Used for Stage 2b/2c debugging.

  --ramp: doubling ramp 1, 2, 4, 8, ..., --users. Random 30-120s per level.
      Stop condition: error rate >= 5% for 30s consecutive (gated on >= 30
      ops in the window). After reaching peak, hold for --hold-minutes M.

On completion (or stop-condition trigger), writes:
  reports/run-<ISO-ts>/summary.md    human-readable
  reports/run-<ISO-ts>/ticks.csv     per-5s samples

Usage:
  # Fixed-level (debugging):
  python run-phase2.py --duration 60 --users 4 --no-ramp

  # Full ramp to 64 users, hold 5 min at peak:
  python run-phase2.py --users 64 --hold-minutes 5

  # Short ramp for testing the mechanics:
  python run-phase2.py --users 4 --hold-minutes 0
"""

from __future__ import annotations

import argparse
import asyncio
import datetime as _dt
import json
import sys
import time
from pathlib import Path


HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

from lib.worker import VirtualUser, WorkerConfig  # noqa: E402
from lib.metrics import MetricsCollector, aggregate_percentiles, iter_snapshot_rows  # noqa: E402
from lib.reporter import run_reporter, run_drainer  # noqa: E402
from lib.ops import OpResult, set_fail_log_path, log_failure_counters  # noqa: E402
from lib.ramp import RampPlan, StopCondition, Level  # noqa: E402
from lib.reports import (  # noqa: E402
    RunRecord, TickRecord, LevelResult, make_run_dir,
    write_summary_md, write_summary_html, write_ticks_csv,
)


SESSIONS_JSON = HERE / "sessions.json"
REPORTS_DIR = HERE / "reports"


def _activate_fail_log(args: argparse.Namespace, started_wall: "_dt.datetime") -> "Path | None":
    """Decide where to write the per-failure debug log (or disable it).

    Returns the run-dir Path that was created (so writes at end of run land
    there too), or None if we couldn't set it up. Call this BEFORE spawning
    any workers so failures are captured from the first request.
    """
    if args.fail_log == "off":
        set_fail_log_path(None)
        return None
    REPORTS_DIR.mkdir(exist_ok=True)
    run_dir = make_run_dir(REPORTS_DIR, started_wall)
    if args.fail_log == "auto":
        path = run_dir / "failures.log"
    else:
        path = Path(args.fail_log)
        path.parent.mkdir(parents=True, exist_ok=True)
    # Open for append; clear it first so reruns of the SAME --fail-log
    # don't accumulate stale entries.
    path.write_text("")
    set_fail_log_path(str(path))
    print(f"#   fail-log: {path.relative_to(HERE) if path.is_relative_to(HERE) else path}")
    return run_dir


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description="Phase 2: fixed-level OR doubling ramp")
    p.add_argument("--users", type=int, default=1, help="Max concurrent users (peak of ramp)")
    p.add_argument("--mode", choices=["random", "scripted"], default="random")
    p.add_argument("--username", help="Pin all virtual users to one real user (debug)")
    p.add_argument("--url", help="Base URL override (default: from sessions.json)")
    p.add_argument("--timeout", type=float, default=30.0)
    p.add_argument("--silent-ops", action="store_true",
                   help="Suppress per-op lines; keep live metrics + summary only")
    p.add_argument("--fail-log", type=str, default="auto", metavar="PATH",
                   help="Append per-failure detail (headers, body snippet) to this "
                        "file. Default 'auto' writes to <run-dir>/failures.log. "
                        "Pass 'off' to disable.")

    # Ramp-mode flags
    ramp_group = p.add_mutually_exclusive_group()
    ramp_group.add_argument("--ramp", dest="ramp", action="store_true", default=None,
                            help="Doubling ramp (1 → 2 → 4 → ... → --users)")
    ramp_group.add_argument("--no-ramp", dest="ramp", action="store_false", default=None,
                            help="Fixed level at --users for --duration seconds")
    p.add_argument("--hold-minutes", type=float, default=0.0,
                   help="Hold at peak after ramp completes (default: 0)")

    # Fixed-level flag
    p.add_argument("--duration", type=float, default=60.0,
                   help="Seconds to run in --no-ramp mode (default: 60)")
    return p.parse_args()


def load_sessions() -> dict:
    if not SESSIONS_JSON.exists():
        print(f"sessions.json not found. Run load-test.py first.", file=sys.stderr)
        sys.exit(2)
    return json.loads(SESSIONS_JSON.read_text())


def pick_users(sess: dict, n: int, preferred: str | None) -> list[dict]:
    users = sess.get("users", [])
    if not users:
        print("sessions.json has no users", file=sys.stderr)
        sys.exit(2)
    if preferred:
        match = next((u for u in users if u.get("username") == preferred), None)
        if not match:
            print(f"--username {preferred} not in sessions.json", file=sys.stderr)
            sys.exit(2)
        return [match] * n
    if n > len(users):
        print(f"requested --users {n} but sessions.json only has {len(users)} — "
              f"run `load-test.py --users {n}` first to provision more",
              file=sys.stderr)
        sys.exit(2)
    return users[:n]


async def worker_task(
    user: dict,
    sess: dict,
    args: argparse.Namespace,
    emit: "callable",
    duration_s: float,
) -> None:
    """Run one VirtualUser for duration_s seconds. Upload payloads are
    generated in-worker (see lib/worker.py); no external corpus needed."""
    cfg = WorkerConfig(
        base_url=(args.url or sess["base_url"]).rstrip("/"),
        username=user["username"],
        password=user["password"],
        session_uuid=user.get("uuid", ""),
        admin_uuid=sess["admin_uuid"],
        timeout_seconds=args.timeout,
    )
    vu = VirtualUser(cfg, on_result=emit)
    if args.mode == "random":
        await vu.run_random_mix(duration_s)
    else:
        await vu.run_scripted(duration_s)


# ------------------------------------------------------------------
# Ramp-mode orchestrator
# ------------------------------------------------------------------

class RampState:
    """Shared mutable state passed between the ramp driver, the reporter
    tick callback, and the per-level snapshot capture."""

    def __init__(self):
        self.active_user_count: int = 0
        self.ticks: list[TickRecord] = []
        self.stop_triggered: bool = False
        self.stop_reason: str | None = None


async def run_ramp(args: argparse.Namespace) -> int:
    sess = load_sessions()
    if not sess.get("base_url") and not args.url:
        print("--url required (not in sessions.json)", file=sys.stderr)
        return 2
    if not sess.get("admin_uuid"):
        print("sessions.json missing admin_uuid", file=sys.stderr)
        return 2

    users_pool = pick_users(sess, args.users, args.username)

    plan = RampPlan(max_users=args.users)
    hold_seconds = args.hold_minutes * 60.0
    total_ramp_s = plan.total_seconds
    total_run_s = total_ramp_s + hold_seconds

    print(f"# Phase 2d ramp: {plan.describe()}")
    if hold_seconds:
        print(f"#   peak hold: {int(hold_seconds)}s at {args.users} users "
              f"(total: {int(total_run_s)}s)")
    print(f"#   mode={args.mode} url={(args.url or sess['base_url'])}")
    print(f"#   pool={len(users_pool)} accounts  (upload payloads generated at runtime)")
    print()

    # ---------- pipeline ----------
    queue: asyncio.Queue[OpResult] = asyncio.Queue()
    collector = MetricsCollector()
    stop_event = asyncio.Event()          # workers watch this via their deadlines indirectly
    stop_condition = StopCondition()
    state = RampState()

    started_wall = _dt.datetime.now(_dt.timezone.utc)
    start_ts = time.monotonic()
    run_deadline = start_ts + total_run_s

    # Create run dir + wire fail-log BEFORE workers spawn
    run_dir = _activate_fail_log(args, started_wall)

    def emit(username: str, r: OpResult) -> None:
        queue.put_nowait(r)
        if not args.silent_ops:
            print(f"[{username:>14s}] {r}")

    # Reporter tick callback: capture TickRecord + evaluate stop condition
    def on_tick(t_elapsed: float, user_count: int, snap) -> None:
        p50, p95 = aggregate_percentiles(snap)
        ops_per_s = snap.total_ops / snap.window_seconds if snap.window_seconds else 0.0
        get_ms = snap.by_method.get("GET")
        post_ms = snap.by_method.get("POST")
        state.ticks.append(TickRecord(
            t_elapsed_s=t_elapsed,
            user_count=user_count,
            window_ops=snap.total_ops,
            window_ok=snap.total_ok,
            window_fail=snap.total_fail,
            ops_per_s=ops_per_s,
            p50_ms=p50,
            p95_ms=p95,
            error_rate=snap.error_rate,
            get_ops=get_ms.total_ops if get_ms else 0,
            get_p50_ms=get_ms.p50_ms if get_ms else 0.0,
            get_p95_ms=get_ms.p95_ms if get_ms else 0.0,
            get_error_rate=get_ms.error_rate if get_ms else 0.0,
            post_ops=post_ms.total_ops if post_ms else 0,
            post_p50_ms=post_ms.p50_ms if post_ms else 0.0,
            post_p95_ms=post_ms.p95_ms if post_ms else 0.0,
            post_error_rate=post_ms.error_rate if post_ms else 0.0,
        ))
        evaluation = stop_condition.evaluate(snap)
        if evaluation.should_stop and not state.stop_triggered:
            state.stop_triggered = True
            state.stop_reason = evaluation.reason
            print(f"# STOP CONDITION: {evaluation.reason}", flush=True)
            stop_event.set()

    drainer = asyncio.create_task(run_drainer(queue, collector, stop_event))
    reporter = asyncio.create_task(run_reporter(
        collector,
        get_user_count=lambda: state.active_user_count,
        stop_event=stop_event,
        start_ts=start_ts,
        on_tick=on_tick,
    ))

    worker_tasks: list[asyncio.Task] = []
    level_results: list[LevelResult] = []

    def remaining_s() -> float:
        return max(0.0, run_deadline - time.monotonic())

    try:
        for level_idx, level in enumerate(plan.levels):
            if stop_event.is_set():
                break

            level_start_ts = time.monotonic()
            # Spawn (level.user_count - current) more workers
            while len(worker_tasks) < level.user_count:
                idx = len(worker_tasks)
                user = users_pool[idx]
                # Workers run until run_deadline (shared) — even if we
                # haven't stepped up to their "nominal" level yet. They
                # don't know anything about the ramp; they just do work.
                t = asyncio.create_task(worker_task(
                    user, sess, args, emit,
                    duration_s=remaining_s(),
                ))
                worker_tasks.append(t)

            state.active_user_count = level.user_count
            print(
                f"# [ramp] step {level_idx + 1}/{len(plan.levels)}  "
                f"users={level.user_count}  dur={int(level.duration_seconds)}s",
                flush=True,
            )

            # Sleep for this level (or until stop triggered, or run_deadline)
            level_end = min(level_start_ts + level.duration_seconds, run_deadline)
            while time.monotonic() < level_end and not stop_event.is_set():
                await asyncio.sleep(min(0.25, level_end - time.monotonic()))

            # Snapshot at end of level (steady state — before adding more)
            lvl_snap = collector.snapshot()
            p50, p95 = aggregate_percentiles(lvl_snap)
            actual_duration = time.monotonic() - level_start_ts
            lvl_get = lvl_snap.by_method.get("GET")
            lvl_post = lvl_snap.by_method.get("POST")
            level_results.append(LevelResult(
                user_count=level.user_count,
                planned_duration_s=level.duration_seconds,
                actual_duration_s=actual_duration,
                total_ops=lvl_snap.total_ops,
                total_fail=lvl_snap.total_fail,
                ops_per_s=lvl_snap.total_ops / lvl_snap.window_seconds if lvl_snap.window_seconds else 0.0,
                p50_ms=p50,
                p95_ms=p95,
                error_rate=lvl_snap.error_rate,
                get_ops=lvl_get.total_ops if lvl_get else 0,
                get_p50_ms=lvl_get.p50_ms if lvl_get else 0.0,
                get_p95_ms=lvl_get.p95_ms if lvl_get else 0.0,
                post_ops=lvl_post.total_ops if lvl_post else 0,
                post_p50_ms=lvl_post.p50_ms if lvl_post else 0.0,
                post_p95_ms=lvl_post.p95_ms if lvl_post else 0.0,
            ))

        # Hold at peak (if not stopped)
        if hold_seconds and not stop_event.is_set():
            print(f"# [hold] {int(hold_seconds)}s at {args.users} users", flush=True)
            hold_end = min(time.monotonic() + hold_seconds, run_deadline)
            while time.monotonic() < hold_end and not stop_event.is_set():
                await asyncio.sleep(min(0.25, hold_end - time.monotonic()))

    finally:
        # Stop workers + collector drain
        stop_event.set()
        # Workers will self-terminate when their duration_s elapses. If we
        # stopped early, we cancel them so we don't wait for leftover think
        # time.
        for t in worker_tasks:
            if not t.done():
                t.cancel()
        await asyncio.gather(*worker_tasks, return_exceptions=True)

        await drainer
        reporter.cancel()
        try:
            await reporter
        except asyncio.CancelledError:
            pass

    ended_wall = _dt.datetime.now(_dt.timezone.utc)
    final_snap = collector.snapshot()

    # ---------- final stdout summary ----------
    elapsed = time.monotonic() - start_ts
    print()
    print(f"# ---------- final ({int(elapsed)}s elapsed) ----------")
    print(f"# ramp levels completed: {len(level_results)}/{len(plan.levels)}")
    if state.stop_reason:
        print(f"# STOP: {state.stop_reason}")
    else:
        print(f"# clean completion (no stop trigger)")
    print(f"# window ops: {final_snap.total_ops}  fail={final_snap.total_fail}  "
          f"err={final_snap.error_rate*100:.2f}%")

    # ---------- write reports ----------
    # run_dir was created up-front by _activate_fail_log(); reuse it so the
    # failures.log lives alongside summary.md/summary.html.
    if run_dir is None:
        REPORTS_DIR.mkdir(exist_ok=True)
        run_dir = make_run_dir(REPORTS_DIR, started_wall)
    rec = RunRecord(
        started_iso=started_wall.isoformat(timespec="seconds"),
        ended_iso=ended_wall.isoformat(timespec="seconds"),
        base_url=(args.url or sess["base_url"]).rstrip("/"),
        cli_args=" ".join(sys.argv),
        ramp_plan=plan.levels,
        ticks=state.ticks,
        levels=level_results,
        hold_seconds_requested=hold_seconds,
        stop_reason=state.stop_reason,
        final_snapshot=final_snap,
    )
    write_summary_md(run_dir / "summary.md", rec)
    write_summary_html(run_dir / "summary.html", rec)
    write_ticks_csv(run_dir / "ticks.csv", rec.ticks)
    print(f"# reports written to: {run_dir.relative_to(HERE)}/")
    calls, skipped = log_failure_counters()
    print(f"# fail-log stats: _log_failure called {calls}x, skipped {skipped}x (no path)")

    # Exit code: 1 if stop triggered OR any final failures
    return 1 if (state.stop_triggered or final_snap.total_fail > 0) else 0


# ------------------------------------------------------------------
# Fixed-level mode (existing 2b/2c behavior)
# ------------------------------------------------------------------

async def run_fixed(args: argparse.Namespace) -> int:
    sess = load_sessions()
    if not sess.get("base_url") and not args.url:
        print("--url required (not in sessions.json)", file=sys.stderr)
        return 2
    if not sess.get("admin_uuid"):
        print("sessions.json missing admin_uuid", file=sys.stderr)
        return 2

    users = pick_users(sess, args.users, args.username)

    print(f"# Phase 2 (fixed): mode={args.mode}, users={args.users}, "
          f"duration={args.duration}s")

    # Activate per-failure debug log (goes in reports/run-<ts>/ even though
    # we don't write full reports in fixed mode — future-proofing)
    started_wall = _dt.datetime.now(_dt.timezone.utc)
    _activate_fail_log(args, started_wall)

    queue: asyncio.Queue[OpResult] = asyncio.Queue()
    collector = MetricsCollector()
    stop_event = asyncio.Event()
    start_ts = time.monotonic()
    active_user_count = len(users)

    def emit(username: str, r: OpResult) -> None:
        queue.put_nowait(r)
        if not args.silent_ops:
            print(f"[{username:>14s}] {r}")

    drainer = asyncio.create_task(run_drainer(queue, collector, stop_event))
    reporter = asyncio.create_task(run_reporter(
        collector,
        get_user_count=lambda: active_user_count,
        stop_event=stop_event,
        start_ts=start_ts,
    ))
    worker_tasks = [
        asyncio.create_task(worker_task(u, sess, args, emit, args.duration))
        for u in users
    ]
    await asyncio.gather(*worker_tasks, return_exceptions=True)
    stop_event.set()
    await drainer
    reporter.cancel()
    try:
        await reporter
    except asyncio.CancelledError:
        pass

    snap = collector.snapshot()
    elapsed = time.monotonic() - start_ts
    print()
    print(f"# ---------- final ({int(elapsed)}s elapsed) ----------")
    print(f"# total ops:  {snap.total_ops}  ok={snap.total_ok}  fail={snap.total_fail}")
    print(f"# error rate: {snap.error_rate*100:.2f}%")
    if snap.per_op:
        print(f"# per-op: op             total  fail   p50ms  p95ms  err%")
        for op, total, fail, p50, p95, err in iter_snapshot_rows(snap):
            print(f"#         {op:14s} {total:5d} {fail:5d} {int(p50):6d} {int(p95):6d} {err*100:5.1f}")
    calls, skipped = log_failure_counters()
    print(f"# fail-log stats: _log_failure called {calls}x, skipped {skipped}x (no path)")
    return 0 if snap.total_fail == 0 else 1


async def main(args: argparse.Namespace) -> int:
    # Mode resolution: --ramp / --no-ramp explicit wins; otherwise default
    # to ramp only if --users > 1 AND --hold-minutes was specified or
    # explicitly opted in. Most scripted debugging wants --no-ramp anyway.
    if args.ramp is None:
        # Implicit: ramp only when --users > 1 AND --duration not given
        # Actually, default to no-ramp to avoid surprising anyone upgrading
        # from 2c. Ramp requires explicit --ramp.
        args.ramp = False

    if args.ramp:
        return await run_ramp(args)
    return await run_fixed(args)


if __name__ == "__main__":
    sys.exit(asyncio.run(main(parse_args())))
