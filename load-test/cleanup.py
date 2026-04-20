#!/usr/bin/env python3
"""Cleanup: delete every loadtest_* user via deluser.fn.

Two modes:
  --from-sessions: read sessions.json (fast, only deletes users from the last run)
  --scan:          enumerate users via nodeinfo endpoint or by probing
                   loadtest_001..loadtest_<max>; deletes anyone matching prefix

Default mode is --from-sessions. Use --scan with --max N when sessions.json is
missing or if you want to clean up users from a prior run with a larger N.
"""

from __future__ import annotations

import argparse
import asyncio
import json
import sys
from pathlib import Path

import aiohttp

sys.path.insert(0, str(Path(__file__).resolve().parent))

from lib.client import AlteranteClient, ClientConfig, make_timeout  # noqa: E402
from lib.users import USERNAME_PREFIX, user_triple  # noqa: E402


HERE = Path(__file__).resolve().parent
SESSIONS_JSON = HERE / "sessions.json"


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description="Delete all loadtest_* users")
    p.add_argument("--url", help="Server base URL (default: read from sessions.json)")
    p.add_argument("--admin-pass", required=True)
    p.add_argument("--admin-user", default="admin")
    mode = p.add_mutually_exclusive_group()
    mode.add_argument("--from-sessions", action="store_true", default=True,
                      help="Delete users listed in sessions.json (default)")
    mode.add_argument("--scan", action="store_true",
                      help="Probe loadtest_001..loadtest_<max> and delete any that exist")
    p.add_argument("--max", type=int, default=100,
                   help="Upper bound for --scan mode (default: 100)")
    return p.parse_args()


async def run(args: argparse.Namespace) -> int:
    usernames: list[str] = []
    url = args.url

    if args.scan:
        if not url:
            print("--url required with --scan", file=sys.stderr)
            return 2
        usernames = [user_triple(i, args.max)[0] for i in range(1, args.max + 1)]
    else:
        if not SESSIONS_JSON.exists():
            print(f"sessions.json not found at {SESSIONS_JSON}. "
                  f"Use --scan --url <url> --max <N> to clean by probing.",
                  file=sys.stderr)
            return 2
        data = json.loads(SESSIONS_JSON.read_text())
        url = url or data.get("base_url")
        usernames = [
            u["username"] for u in data.get("users", [])
            if u.get("username", "").startswith(USERNAME_PREFIX)
        ]

    if not url:
        print("--url required (not found in sessions.json)", file=sys.stderr)
        return 2
    if not usernames:
        print("Nothing to delete.")
        return 0

    cfg = ClientConfig(base_url=url.rstrip("/"))
    print(f"Cleanup: {len(usernames)} candidate users against {cfg.base_url}")

    connector = aiohttp.TCPConnector(limit_per_host=0, force_close=True)
    async with aiohttp.ClientSession(
        connector=connector,
        timeout=make_timeout(30.0),
        cookie_jar=aiohttp.DummyCookieJar(),
    ) as session:
        admin = AlteranteClient(cfg, session)
        if not await admin.login(args.admin_user, args.admin_pass):
            print("FAIL: admin login failed")
            return 1

        deleted = not_found = errors = 0
        for u in usernames:
            status = await admin.delete_user(u)
            if status == "success":
                deleted += 1
            elif status == "notfound":
                not_found += 1
            else:
                errors += 1
                print(f"  {u}: {status}")

        print(f"deleted:  {deleted}")
        print(f"notfound: {not_found}")
        print(f"errors:   {errors}")

        # Remove sessions.json so a follow-up run starts clean
        if not args.scan and SESSIONS_JSON.exists():
            SESSIONS_JSON.unlink()
            print(f"removed {SESSIONS_JSON.name}")

        return 0 if errors == 0 else 3


if __name__ == "__main__":
    sys.exit(asyncio.run(run(parse_args())))
