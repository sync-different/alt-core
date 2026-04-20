"""Async HTTP client for Alterante. One instance per simulated user.

Thin wrapper around aiohttp.ClientSession that handles:
- login.fn (parses session UUID from embedded <input type=hidden id=session-uuid>)
- logout.fn
- adduser.fn / deluser.fn (admin-gated)
- query.fn
- getfile.fn (full + chunked)
- Uploads via port 8081 raw-body POST (not Netty/8087)

The session UUID is stored on the instance after login(). It is passed as a
`uuid` query parameter on subsequent calls, which matches how uiv3/uiv4/uiv5
authenticate. Cookies are also kept (aiohttp default) so either mechanism works.
"""

from __future__ import annotations

import re
from dataclasses import dataclass
from typing import Optional

import aiohttp


_UUID_RE = re.compile(r"session-uuid'\s*value='([0-9a-f-]{36})'")

# Chunk size for the 8081 → Netty multipart upload path. Each .p chunk is
# POSTed separately; matches uiv5's chunked upload over HTTPS. The server's
# processPost() detects .p-suffixed filenames and relays the full HTTP
# request to Netty, which handles multipart parsing and chunk reassembly.
# 512 KB keeps each chunk comfortably within the web server's single-read
# buffer window (BUF_SIZE = 1 MB in Worker.handleClient()).
UPLOAD_CHUNK_BYTES = 512 * 1024


@dataclass
class ClientConfig:
    base_url: str                    # e.g. http://localhost:8081 — the web server root
    timeout_seconds: float = 30.0


class AlteranteClient:
    def __init__(self, cfg: ClientConfig, session: aiohttp.ClientSession):
        self._cfg = cfg
        self._session = session
        self.username: Optional[str] = None
        self.uuid: Optional[str] = None

    @property
    def is_authenticated(self) -> bool:
        return self.uuid is not None

    async def login(self, username: str, password: str) -> bool:
        url = f"{self._cfg.base_url}/cass/login.fn"
        params = {"boxuser": username, "boxpass": password}
        async with self._session.get(url, params=params) as resp:
            body = await resp.text()
            match = _UUID_RE.search(body)
            if not match:
                return False
            self.username = username
            self.uuid = match.group(1)
            return True

    async def logout(self) -> bool:
        if not self.uuid:
            return True
        url = f"{self._cfg.base_url}/cass/logout.fn"
        params = {"uuid": self.uuid}
        try:
            async with self._session.get(url, params=params) as resp:
                await resp.read()
        finally:
            self.uuid = None
            self.username = None
        return True

    async def add_user(self, username: str, password: str, email: str) -> str:
        """Admin-only. Returns 'success', 'alreadyexists', 'error', 'forbidden'."""
        assert self.uuid, "must be logged in as admin"
        url = f"{self._cfg.base_url}/cass/adduser.fn"
        params = {
            "boxuser": username,
            "boxpass": password,
            "useremail": email,
            "uuid": self.uuid,
        }
        async with self._session.get(url, params=params) as resp:
            return (await resp.text()).strip()

    async def delete_user(self, username: str) -> str:
        """Admin-only. Returns 'success', 'notfound', 'forbidden', 'error'."""
        assert self.uuid, "must be logged in as admin"
        url = f"{self._cfg.base_url}/cass/deluser.fn"
        params = {"boxuser": username, "uuid": self.uuid}
        async with self._session.get(url, params=params) as resp:
            return (await resp.text()).strip()

    async def query(
        self,
        ftype: str = ".all",
        foo: str = ".all",
        numobj: int = 50,
        days: str = "",
        order: str = "Desc",
        screen_size: int = 160,
    ) -> dict:
        """Query files. `foo` is the tag filter (".all" means all files).
        Matches uiv5 fetchFiles() params."""
        assert self.uuid
        url = f"{self._cfg.base_url}/cass/query.fn"
        params = {
            "ftype": ftype,
            "foo": foo or ".all",
            "days": days,
            "view": "json",
            "numobj": str(numobj),
            "order": order,
            "screenSize": str(screen_size),
            "uuid": self.uuid,
        }
        # Explicit cookie so self.uuid always wins over whatever the aiohttp
        # cookie jar holds from a prior login.
        headers = {"Cookie": f"uuid={self.uuid}"}
        async with self._session.get(url, params=params, headers=headers) as resp:
            text = await resp.text()
            try:
                import json
                return json.loads(text)
            except Exception:
                return {"raw": text, "error": "non-json"}

    async def get(self, path: str, params: Optional[dict] = None, headers: Optional[dict] = None) -> tuple[int, bytes, dict]:
        """Generic GET. Returns (status, body_bytes, response_headers).

        NOTE: the explicit Cookie header works ONLY when the aiohttp session
        was created with cookie_jar=DummyCookieJar — otherwise aiohttp will
        add its own Cookie header from the jar AFTER ours, and the server's
        cookie-over-query-param precedence will auth as the wrong user. All
        ClientSessions in this project must use DummyCookieJar.
        """
        assert self.uuid
        url = f"{self._cfg.base_url}{path}"
        merged = dict(params or {})
        merged["uuid"] = self.uuid
        hdrs = dict(headers or {})
        # Override cookie so this GET always auths as self.uuid even when
        # the cookie jar holds a different session uuid.
        hdrs["Cookie"] = f"uuid={self.uuid}"
        async with self._session.get(url, params=merged, headers=hdrs) as resp:
            body = await resp.read()
            return resp.status, body, dict(resp.headers)

    async def nodeinfo(self) -> dict:
        assert self.uuid
        url = f"{self._cfg.base_url}/cass/nodeinfo.fn"
        params = {"uuid": self.uuid}
        async with self._session.get(url, params=params) as resp:
            text = await resp.text()
            try:
                import json
                return json.loads(text)
            except Exception:
                return {"raw": text}

    async def upload_file(self, filename: str, data: bytes) -> tuple[int, int, int]:
        """Upload a file via the 8081 → Netty .p-chunk relay.

        Matches uiv5's HTTPS upload path (smoke test 6.13/6.14): each chunk is
        POSTed as multipart/form-data to `http://host:8081/upload.<fname>.<T>.<I>.p`
        with one form field named `upload.<fname>.<T>.<I>.p` holding the chunk
        bytes. The server's processPost() detects the .p suffix and forwards
        the raw HTTP request to Netty via connectToNetty(). Netty parses the
        multipart form and writes the chunk to rtserver/incoming/. When the
        last chunk arrives, ProcessorService reassembles the file.

        IMPORTANT: aiohttp sends `Expect: 100-continue` by default on large
        multipart bodies. The alt-core WebServer's home-rolled parser does NOT
        send the 100-continue interim response, which causes the connection to
        hang or reset. We override Expect to empty to force a simple POST.

        Returns (chunks_total, chunks_ok, http_last).
        """
        assert self.uuid, "must be logged in"
        from urllib.parse import quote

        total = max(1, (len(data) + UPLOAD_CHUNK_BYTES - 1) // UPLOAD_CHUNK_BYTES)
        ok = 0
        last_status = 0

        for idx in range(total):
            start = idx * UPLOAD_CHUNK_BYTES
            end = min(start + UPLOAD_CHUNK_BYTES, len(data))
            chunk = data[start:end]
            param_name = f"upload.{filename}.{total}.{idx + 1}.p"
            url = f"{self._cfg.base_url}/{quote(param_name, safe='')}"

            form = aiohttp.FormData()
            # Field name == filename == param_name (matches uiv5 UploadZone)
            form.add_field(
                param_name,
                chunk,
                filename=param_name,
                content_type="application/octet-stream",
            )
            # Connection: close — the WebServer returns HTTP/1.0 responses
            # which disable keep-alive anyway, but aiohttp's connection pool
            # will otherwise try to reuse the socket and see a reset. Forcing
            # close makes each chunk a fresh TCP connection.
            #
            # Cookie: the 8081 POST path only accepts cookie auth (not ?uuid=),
            # so set it explicitly so uploads work even when login() was never
            # called on this client (e.g., bench.py installs self.uuid directly).
            headers = {
                "Expect": "",
                "Connection": "close",
                "Cookie": f"uuid={self.uuid}",
            }
            async with self._session.post(url, data=form, headers=headers) as resp:
                last_status = resp.status
                await resp.read()
                if resp.status == 200:
                    ok += 1

        return total, ok, last_status


def make_timeout(seconds: float) -> aiohttp.ClientTimeout:
    return aiohttp.ClientTimeout(total=seconds)
