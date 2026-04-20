"""Seed corpus check + upload. Ensures the server has at least N files indexed
so load-test download/query scenarios have real data to exercise.

Strategy:
1. Query nTotal from query.fn (no filter, start=0, count=1) — cheap server-side count.
2. If nTotal < threshold, upload every file in test-files/ via the
   8081 → Netty .p-chunk relay (same path uiv5 uses over HTTPS).
3. We do NOT wait for indexing here — indexing can take 30-45s and only
   matters for Phase 2+.

We deliberately do NOT generate synthetic files: we want real content (varied
sizes/types) so the test exercises the same code paths real users hit.
"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Optional

from .client import AlteranteClient


@dataclass
class SeedResult:
    nTotal_before: int
    needed_seed: bool
    files_uploaded: int
    files_failed: int
    error: Optional[str] = None


async def get_total_indexed(admin: AlteranteClient) -> int:
    resp = await admin.query(numobj=1)
    try:
        return int(resp["objFound"][0]["nTotal"])
    except (KeyError, IndexError, ValueError, TypeError):
        return 0


async def seed_if_needed(
    admin: AlteranteClient,
    test_files_dir: Path,
    threshold: int,
) -> SeedResult:
    n_before = await get_total_indexed(admin)
    if n_before >= threshold:
        return SeedResult(n_before, needed_seed=False, files_uploaded=0, files_failed=0)

    if not test_files_dir.exists():
        return SeedResult(n_before, True, 0, 0, error=f"{test_files_dir} does not exist")

    candidates = [
        p for p in sorted(test_files_dir.iterdir())
        if p.is_file() and not p.name.startswith(".")
    ]
    if not candidates:
        return SeedResult(
            n_before, True, 0, 0,
            error=f"{test_files_dir} is empty — add seed files and re-run",
        )

    # Upload files concurrently — serial upload of 13 files is ~30-60s because
    # each file does fresh TCP handshakes per 512KB chunk. Parallelizing drops
    # that to max-file-latency (~3-5s). Server handles concurrent uploads fine
    # since each goes through its own worker thread.
    import asyncio

    async def _upload_one(path):
        try:
            data = path.read_bytes()
            total, ok, last = await admin.upload_file(filename=path.name, data=data)
            if ok == total:
                print(f"    ok: {path.name} ({len(data)} bytes, {total} chunk{'s' if total > 1 else ''})")
                return (True, None)
            else:
                print(f"    FAIL: {path.name} ({len(data)} bytes, {ok}/{total} chunks, last HTTP {last})")
                return (False, f"{ok}/{total} chunks")
        except Exception as ex:
            print(f"    EXC: {path.name} - {type(ex).__name__}: {ex}")
            return (False, str(ex))

    results = await asyncio.gather(*(_upload_one(p) for p in candidates), return_exceptions=False)
    uploaded = sum(1 for ok, _ in results if ok)
    failed = sum(1 for ok, _ in results if not ok)

    return SeedResult(
        nTotal_before=n_before,
        needed_seed=True,
        files_uploaded=uploaded,
        files_failed=failed,
    )
