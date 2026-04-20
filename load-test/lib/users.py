"""User pool management for load test.

Deterministic naming scheme (agreed 2026-04-19):
  username: loadtest_001 ... loadtest_N  (zero-padded to width of N)
  password: LoadTest!001 ... LoadTest!N  (same suffix as username)
  email:    loadtest_001@test.invalid    (RFC 2606 reserved TLD)

Using deterministic credentials means re-running load-test.py with the same
--users count is idempotent: adduser.fn returns 'alreadyexists' for existing
users, and we can still log them in with the known password.
"""

from __future__ import annotations

import asyncio
import json
from dataclasses import dataclass, asdict, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Optional

import aiohttp

from .client import AlteranteClient, ClientConfig, make_timeout


USERNAME_PREFIX = "loadtest_"
PASSWORD_PREFIX = "LoadTest!"
EMAIL_DOMAIN = "test.invalid"


def user_triple(index: int, total: int) -> tuple[str, str, str]:
    width = max(3, len(str(total)))
    suffix = str(index).zfill(width)
    return (
        f"{USERNAME_PREFIX}{suffix}",
        f"{PASSWORD_PREFIX}{suffix}",
        f"{USERNAME_PREFIX}{suffix}@{EMAIL_DOMAIN}",
    )


@dataclass
class UserRecord:
    username: str
    password: str
    email: str
    uuid: Optional[str] = None
    login_ok: bool = False
    create_status: str = ""  # 'success' | 'alreadyexists' | 'error' | 'skipped'


@dataclass
class PoolResult:
    created: int = 0
    already_existed: int = 0
    create_errors: int = 0
    logged_in: int = 0
    login_failures: int = 0
    users: list[UserRecord] = field(default_factory=list)


async def ensure_user(admin: AlteranteClient, rec: UserRecord) -> None:
    status = await admin.add_user(rec.username, rec.password, rec.email)
    rec.create_status = status


async def login_user(cfg: ClientConfig, rec: UserRecord) -> None:
    # force_close=True: HTTP/1.0 server — see note in load-test.py
    connector = aiohttp.TCPConnector(limit_per_host=0, force_close=True)
    async with aiohttp.ClientSession(
        connector=connector,
        timeout=make_timeout(cfg.timeout_seconds),
        cookie_jar=aiohttp.DummyCookieJar(),
    ) as session:
        client = AlteranteClient(cfg, session)
        ok = await client.login(rec.username, rec.password)
        rec.login_ok = ok
        rec.uuid = client.uuid


async def build_user_pool(
    admin: AlteranteClient, cfg: ClientConfig, total_users: int
) -> PoolResult:
    result = PoolResult()
    # Create users sequentially — adduser.fn writes users.txt, and concurrent
    # writes could race. This is a one-time Phase 1 cost; not on hot path.
    for i in range(1, total_users + 1):
        u, p, e = user_triple(i, total_users)
        rec = UserRecord(username=u, password=p, email=e)
        await ensure_user(admin, rec)
        if rec.create_status == "success":
            result.created += 1
        elif rec.create_status == "alreadyexists":
            result.already_existed += 1
        else:
            result.create_errors += 1
        result.users.append(rec)

    # Log in users concurrently — independent sessions, safe to parallelize.
    await asyncio.gather(*(login_user(cfg, r) for r in result.users))
    for r in result.users:
        if r.login_ok:
            result.logged_in += 1
        else:
            result.login_failures += 1
    return result


def persist_sessions(path: Path, cfg: ClientConfig, admin_uuid: str, pool: PoolResult) -> None:
    payload = {
        "created_at": datetime.now(timezone.utc).isoformat(),
        "base_url": cfg.base_url,
        "admin_uuid": admin_uuid,
        "users": [asdict(u) for u in pool.users],
    }
    path.write_text(json.dumps(payload, indent=2))
