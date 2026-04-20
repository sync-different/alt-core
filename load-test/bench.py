#!/usr/bin/env python3
"""Single-op bench CLI — exercises one operation from lib/ops.py and prints
the result. Used to validate each op works before wiring into the Phase 2
worker loop.

Reads sessions.json to get the base_url and to pick a session. If no session
is specified, uses the first user in the pool. Use --as-admin to use the
admin UUID instead.

Usage:
  python bench.py --op search
  python bench.py --op search --arg .all
  python bench.py --op open_image   # auto-picks first indexed file
  python bench.py --op open_image --md5 abc123 --filename foo.jpg --ext jpg
  python bench.py --op tag --md5 abc123 --tag loadtest
  python bench.py --op upload --file ./test-files/small.bin
  python bench.py --op download --md5 abc123 --filename big.bin --ext bin
  python bench.py --op video_stream --md5 abc123 --filename clip.mp4
  python bench.py --op login        # requires --username + --password
  python bench.py --op browse_folders
  python bench.py --op all          # run a sanity sweep across all ops
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
from lib import ops  # noqa: E402


HERE = Path(__file__).resolve().parent
SESSIONS_JSON = HERE / "sessions.json"


OP_CHOICES = [
    "search", "browse_folders", "open_image", "open_pdf",
    "download", "video_stream", "tag", "upload",
    "login", "logout", "all",
]


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description="Single-op bench for Alterante load test")
    p.add_argument("--op", required=True, choices=OP_CHOICES)
    p.add_argument("--url", help="Base URL (default: from sessions.json)")
    # User selection
    p.add_argument("--as-admin", action="store_true",
                   help="Use admin UUID from sessions.json (requires --admin-pass for login op)")
    p.add_argument("--username", help="User to impersonate (default: first in sessions.json)")
    p.add_argument("--password", help="Needed only for --op login")
    # Op args
    p.add_argument("--arg", help="Free-form arg for ops that accept one (e.g. search foo)")
    p.add_argument("--md5", help="File MD5 (sNamer) for file ops; auto-picked if omitted")
    p.add_argument("--filename", help="Filename for file ops")
    p.add_argument("--ext", help="File extension (jpg, pdf, mp4, bin, ...)")
    p.add_argument("--tag", help="Tag name for --op tag")
    p.add_argument("--file", help="Local file path for --op upload")
    p.add_argument("--segments", type=int, default=3,
                   help="Number of HLS .ts segments to fetch for --op video_stream")
    p.add_argument("--timeout", type=float, default=30.0)
    return p.parse_args()


def load_sessions() -> dict:
    if not SESSIONS_JSON.exists():
        print(f"sessions.json not found at {SESSIONS_JSON}. Run load-test.py first.",
              file=sys.stderr)
        sys.exit(2)
    return json.loads(SESSIONS_JSON.read_text())


def resolve_credentials(args: argparse.Namespace, sess: dict) -> tuple[str, str, Optional[str]]:  # type: ignore[name-defined]
    """Return (base_url, chosen_uuid_or_empty, username)."""
    base_url = args.url or sess.get("base_url")
    if not base_url:
        print("--url required (not found in sessions.json)", file=sys.stderr)
        sys.exit(2)

    if args.as_admin:
        return base_url, sess.get("admin_uuid", ""), "admin"

    users = sess.get("users", [])
    if args.username:
        match = next((u for u in users if u.get("username") == args.username), None)
        if not match:
            print(f"--username {args.username} not in sessions.json", file=sys.stderr)
            sys.exit(2)
    else:
        if not users:
            print("sessions.json has no users", file=sys.stderr)
            sys.exit(2)
        match = users[0]
    return base_url, match.get("uuid", ""), match.get("username", "")


async def run_op(args: argparse.Namespace) -> int:
    from typing import Optional
    sess = load_sessions() if args.op != "login" or not args.url else {}
    if args.op == "login":
        # login needs explicit creds; may not have sessions.json yet
        if not args.url or not args.username or not args.password:
            print("--op login requires --url, --username, --password", file=sys.stderr)
            return 2
        base_url = args.url
        chosen_uuid = ""
        who = args.username
    else:
        base_url, chosen_uuid, who = resolve_credentials(args, sess)

    cfg = ClientConfig(base_url=base_url.rstrip("/"), timeout_seconds=args.timeout)
    connector = aiohttp.TCPConnector(limit_per_host=0, force_close=True)

    async with aiohttp.ClientSession(
        connector=connector,
        timeout=make_timeout(args.timeout),
        cookie_jar=aiohttp.DummyCookieJar(),
    ) as session:
        client = AlteranteClient(cfg, session)
        # Install the pre-obtained uuid so ops that need auth work without
        # a fresh login. (login op reassigns uuid.)
        if chosen_uuid:
            client.uuid = chosen_uuid
            client.username = who

        print(f"# bench: op={args.op} as={who} url={base_url}")

        if args.op == "all":
            return await run_all_sanity(client, args) or 0

        r = await dispatch_one(client, args)
        print(r)
        return 0 if r.ok else 1


async def dispatch_one(client: AlteranteClient, args: argparse.Namespace):
    op = args.op
    if op == "search":
        return await ops.op_search(client, foo=args.arg or ".all")
    if op == "browse_folders":
        return await ops.op_browse_folders(client)
    if op == "login":
        return await ops.op_login(client, args.username, args.password)
    if op == "logout":
        return await ops.op_logout(client)

    # File-centric ops: resolve md5/filename/ext from args or auto-pick
    md5 = args.md5
    filename = args.filename
    ext = args.ext
    if op in ("open_image", "open_pdf", "download", "video_stream", "tag"):
        if not md5:
            if op == "video_stream":
                picked = await ops.pick_video(client)
                err = "no video files in corpus (need mp4/mov/etc.)"
            else:
                picked = await ops.pick_file(client)
                err = "no indexed files to pick from"
            if not picked:
                from lib.ops import OpResult
                return OpResult(op, False, 0.0, error=err)
            md5 = picked.get("nickname")
            filename = filename or picked.get("name")
            ext = ext or picked.get("file_ext", "bin")
            print(f"# auto-picked file: md5={md5} name={filename} ext={ext}")

    if op == "open_image":
        return await ops.op_open_image(client, md5, filename or "x.jpg", ext or "jpg")
    if op == "open_pdf":
        return await ops.op_open_pdf(client, md5, filename or "x.pdf")
    if op == "download":
        return await ops.op_download(client, md5, filename or "x.bin", ext or "bin")
    if op == "video_stream":
        return await ops.op_video_stream(client, md5, filename or "x.mp4", ext or "mp4",
                                         n_segments=args.segments)
    if op == "tag":
        tag = args.tag or "loadtest"
        return await ops.op_tag(client, md5, tag)
    if op == "upload":
        if not args.file:
            from lib.ops import OpResult
            return OpResult(op, False, 0.0, error="--file required for upload")
        data = Path(args.file).read_bytes()
        return await ops.op_upload(client, Path(args.file).name, data)

    raise ValueError(f"unhandled op: {op}")


async def run_all_sanity(client: AlteranteClient, args: argparse.Namespace) -> int:
    """Run one of each op against a suitable file. Uses the first indexed
    file for generic ops, and the first video for video_stream."""
    picked = await ops.pick_file(client)
    if not picked:
        print("no indexed files — seed first, then retry --op all")
        return 2
    md5 = picked["nickname"]
    filename = picked["name"]
    ext = picked.get("file_ext", "bin")
    print(f"# using file: name={filename} md5={md5} ext={ext}")

    # video_stream wants a real video; fall back gracefully if none exists
    video = await ops.pick_video(client)
    if video:
        print(f"# using video: name={video['name']} md5={video['nickname']} ext={video.get('file_ext','?')}")
    else:
        print(f"# (no videos in corpus — video_stream op will report empty manifest)")

    results = []
    results.append(await ops.op_search(client))
    results.append(await ops.op_browse_folders(client))
    results.append(await ops.op_open_image(client, md5, filename, ext))
    results.append(await ops.op_open_pdf(client, md5, filename))
    results.append(await ops.op_download(client, md5, filename, ext))
    if video:
        results.append(await ops.op_video_stream(
            client, video["nickname"], video["name"], video.get("file_ext", "mp4")))
    else:
        results.append(await ops.op_video_stream(client, md5, filename, ext))
    results.append(await ops.op_tag(client, md5, "bench_sanity"))

    fails = 0
    for r in results:
        print(r)
        if not r.ok:
            fails += 1
    print(f"# summary: {len(results)-fails} ok / {len(results)} total")
    return fails


if __name__ == "__main__":
    sys.exit(asyncio.run(run_op(parse_args())))
