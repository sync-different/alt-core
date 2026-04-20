#!/usr/bin/env python3
"""Load test entry point — Phase 1: setup.

Phase 1 steps:
  1. Admin login.
  2. Seed check: upload test-files/* if indexed count < max(20, users*2).
  3. Create loadtest_001 ... loadtest_N via adduser.fn (idempotent).
  4. Log each user in, collect session UUIDs.
  5. Persist sessions.json; print summary.

Later phases (2+) will read sessions.json and drive concurrent traffic.

Usage:
  python load-test.py --url http://localhost:8081 --users 3 \\
      --admin-pass valid [--admin-user admin] [--seed-if-missing]

Exit codes:
  0 = Phase 1 succeeded (all users created/existing and logged in)
  1 = admin login failed
  2 = seed required but test-files/ is empty or missing
  3 = one or more users failed to log in
"""

from __future__ import annotations

import argparse
import asyncio
import json
import sys
from pathlib import Path

import aiohttp

# Allow running as `python load-test.py` (no package install needed)
sys.path.insert(0, str(Path(__file__).resolve().parent))

from lib.client import AlteranteClient, ClientConfig, make_timeout  # noqa: E402
from lib.seed import seed_if_needed  # noqa: E402
from lib.users import build_user_pool, persist_sessions  # noqa: E402


HERE = Path(__file__).resolve().parent
# Default corpus: the real mixed-format seed files at the repo root
# (../test-files/). These are JPG, PNG, PDF, DOCX, XLSX, PPTX, MP4, MP3,
# etc. — real file types alt-core will index. A user can override with
# --test-files-dir if they want to point at a different corpus.
TEST_FILES_DIR = HERE.parent / "test-files"
SESSIONS_JSON = HERE / "sessions.json"


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description="Alterante load test — Phase 1 (setup)")
    p.add_argument("--url", required=True, help="Server base URL, e.g. http://localhost:8081")
    p.add_argument("--users", type=int, required=True, help="Number of simulated users to create")
    p.add_argument("--admin-pass", required=True, help="Admin password")
    p.add_argument("--admin-user", default="admin", help="Admin username (default: admin)")
    p.add_argument("--seed-if-missing", action="store_true",
                   help="Upload test-files/* if indexed count is below threshold")
    p.add_argument("--test-files-dir", type=Path, default=TEST_FILES_DIR,
                   help=f"Seed corpus directory (default: {TEST_FILES_DIR})")
    p.add_argument("--timeout", type=float, default=30.0,
                   help="Per-request timeout in seconds (default: 30)")
    return p.parse_args()


async def run(args: argparse.Namespace) -> int:
    cfg = ClientConfig(base_url=args.url.rstrip("/"), timeout_seconds=args.timeout)
    print(f"[1/4] Admin login to {cfg.base_url} as {args.admin_user}")

    # force_close=True: disable keep-alive. The alt-core web server returns
    # HTTP/1.0 responses without keep-alive headers, so any pooled connection
    # is effectively dead by the time we try to reuse it. Forcing close
    # sidesteps "Connection reset by peer" on the second request to a socket
    # aiohttp thought was still open.
    connector = aiohttp.TCPConnector(limit_per_host=0, force_close=True)
    async with aiohttp.ClientSession(
        connector=connector,
        timeout=make_timeout(args.timeout),
        cookie_jar=aiohttp.DummyCookieJar(),
    ) as session:
        admin = AlteranteClient(cfg, session)
        ok = await admin.login(args.admin_user, args.admin_pass)
        if not ok:
            print(f"  FAIL: admin login failed (wrong password? rate-limited?)")
            return 1
        print(f"  OK: admin uuid={admin.uuid}")

        # Step 2: seed check
        threshold = max(20, args.users * 2)
        print(f"[2/4] Seed check (threshold = {threshold} files indexed)")
        if args.seed_if_missing:
            result = await seed_if_needed(admin, args.test_files_dir, threshold)
            if result.error:
                print(f"  FAIL: {result.error}")
                return 2
            if not result.needed_seed:
                print(f"  OK: already have {result.nTotal_before} files indexed")
            else:
                print(f"  Seeded: had {result.nTotal_before}, "
                      f"uploaded {result.files_uploaded}, failed {result.files_failed}")
                print(f"  (note: background indexing may take ~30-45s)")
        else:
            print(f"  SKIP: --seed-if-missing not passed")

        # Step 3-4: user pool
        print(f"[3/4] Creating user pool (users={args.users})")
        pool = await build_user_pool(admin, cfg, args.users)
        print(f"  created:         {pool.created}")
        print(f"  already existed: {pool.already_existed}")
        print(f"  create errors:   {pool.create_errors}")
        print(f"  logged in:       {pool.logged_in}")
        print(f"  login failures:  {pool.login_failures}")

        # Step: persist
        print(f"[4/4] Persisting sessions to {SESSIONS_JSON.relative_to(HERE)}")
        persist_sessions(SESSIONS_JSON, cfg, admin.uuid, pool)

        if pool.login_failures > 0:
            print(f"\nPhase 1 partial: {pool.login_failures} users failed to log in")
            return 3
        if pool.create_errors > 0:
            print(f"\nPhase 1 partial: {pool.create_errors} users failed to create")
            return 3

        print(f"\nPhase 1 OK — ready to run Phase 2 with {pool.logged_in} users")
        return 0


def main() -> int:
    args = parse_args()
    if args.users < 1:
        print("--users must be >= 1", file=sys.stderr)
        return 2
    return asyncio.run(run(args))


if __name__ == "__main__":
    sys.exit(main())
