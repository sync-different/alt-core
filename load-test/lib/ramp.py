"""Ramp plan + stop-condition evaluator for Stage 2d.

RampPlan
--------
Generates the sequence of user counts to run at, in order. Doubling by
default (1, 2, 4, 8, ...) up to max_users. If max_users isn't a power of
two, the last level lands on max_users exactly.

Example: max_users=10 → [1, 2, 4, 8, 10]

StopCondition
-------------
Evaluates a rolling metrics snapshot against "error rate >= ERROR_THRESHOLD
for STOP_HOLD_SECONDS consecutive seconds, gated on at least MIN_OPS in the
window." Called from the reporter task each tick.

Spec (SPEC_LOAD_TEST.md Q7 final answer, line 92-96):
  - stop if errors > 5% for 30s consecutive
  - gated on >= 30 ops observed in the window
  - ramp doubling, 30-120s per level (random)
"""

from __future__ import annotations

import random
import time
from dataclasses import dataclass
from typing import Iterator, Optional

from .metrics import Snapshot


ERROR_THRESHOLD = 0.05         # 5 %
STOP_HOLD_SECONDS = 30.0       # consecutive-seconds window
MIN_OPS_FOR_GATE = 30

LEVEL_MIN_SECONDS = 30
LEVEL_MAX_SECONDS = 120


@dataclass
class Level:
    user_count: int
    duration_seconds: float


class RampPlan:
    """Ordered ramp levels. The duration for each level is randomised at
    instantiation so repeated runs are reproducible within a single run
    but vary between runs (matches the spec's realistic-looking cadence)."""

    def __init__(self, max_users: int, rng: Optional[random.Random] = None):
        if max_users < 1:
            raise ValueError("max_users must be >= 1")
        self._rng = rng or random.Random()
        self._levels: list[Level] = []
        for count in self._doubling_sequence(max_users):
            dur = float(self._rng.randint(LEVEL_MIN_SECONDS, LEVEL_MAX_SECONDS))
            self._levels.append(Level(user_count=count, duration_seconds=dur))

    @staticmethod
    def _doubling_sequence(max_users: int) -> list[int]:
        counts: list[int] = []
        n = 1
        while n < max_users:
            counts.append(n)
            n *= 2
        counts.append(max_users)
        return counts

    def __iter__(self) -> Iterator[Level]:
        return iter(self._levels)

    @property
    def levels(self) -> list[Level]:
        return list(self._levels)

    @property
    def total_seconds(self) -> float:
        return sum(l.duration_seconds for l in self._levels)

    def describe(self) -> str:
        parts = [f"{l.user_count}u@{int(l.duration_seconds)}s" for l in self._levels]
        return " → ".join(parts) + f"  (total: {int(self.total_seconds)}s)"


@dataclass
class StopEvaluation:
    should_stop: bool
    reason: Optional[str] = None
    error_rate: float = 0.0
    sampled_ops: int = 0


class StopCondition:
    """Tracks consecutive-time-above-threshold. Call evaluate() on each
    reporter tick with a fresh snapshot; returns whether to halt.

    State: remembers the monotonic timestamp when we first crossed the
    threshold. Resets when any observation drops back below. Fires once
    we've been above-threshold for STOP_HOLD_SECONDS.
    """

    def __init__(
        self,
        error_threshold: float = ERROR_THRESHOLD,
        hold_seconds: float = STOP_HOLD_SECONDS,
        min_ops: int = MIN_OPS_FOR_GATE,
    ):
        self._error_threshold = error_threshold
        self._hold_seconds = hold_seconds
        self._min_ops = min_ops
        self._first_crossed_ts: Optional[float] = None

    def reset(self) -> None:
        self._first_crossed_ts = None

    def evaluate(self, snap: Snapshot, now: Optional[float] = None) -> StopEvaluation:
        now = now if now is not None else time.monotonic()
        if snap.total_ops < self._min_ops:
            # Not enough data — reset so short dips don't accumulate
            self._first_crossed_ts = None
            return StopEvaluation(
                should_stop=False,
                error_rate=snap.error_rate,
                sampled_ops=snap.total_ops,
            )

        if snap.error_rate >= self._error_threshold:
            if self._first_crossed_ts is None:
                self._first_crossed_ts = now
                return StopEvaluation(
                    should_stop=False,
                    error_rate=snap.error_rate,
                    sampled_ops=snap.total_ops,
                )
            held = now - self._first_crossed_ts
            if held >= self._hold_seconds:
                return StopEvaluation(
                    should_stop=True,
                    reason=(
                        f"error rate {snap.error_rate*100:.1f}% >= "
                        f"{self._error_threshold*100:.0f}% for {int(held)}s "
                        f"(window ops={snap.total_ops})"
                    ),
                    error_rate=snap.error_rate,
                    sampled_ops=snap.total_ops,
                )
            return StopEvaluation(
                should_stop=False,
                error_rate=snap.error_rate,
                sampled_ops=snap.total_ops,
            )

        # Below threshold — reset
        self._first_crossed_ts = None
        return StopEvaluation(
            should_stop=False,
            error_rate=snap.error_rate,
            sampled_ops=snap.total_ops,
        )
