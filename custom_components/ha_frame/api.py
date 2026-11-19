"""Async HTTP client for the opt-in PF1007 integration endpoint."""
from __future__ import annotations

import asyncio
from typing import Any

from aiohttp import ClientSession, ClientError


class FrameApiError(Exception):
    """The frame rejected or failed a request."""


class FrameApi:
    def __init__(self, session: ClientSession, host: str, token: str = "") -> None:
        host = host.strip()
        if host.startswith("http://"):
            host = host[7:]
        if "/" in host or "@" in host or "?" in host or "#" in host:
            raise FrameApiError("Use only the frame's LAN address")
        if ":" not in host:
            host += ":8766"
        self.base = f"http://{host}"
        self.session = session
        self.token = token

    async def call(self, method: str, path: str, payload: dict[str, Any] | None = None) -> dict:
        headers = {"Authorization": f"Bearer {self.token}"} if self.token else {}
        try:
            async with asyncio.timeout(8):
                async with self.session.request(method, self.base + path, json=payload, headers=headers) as response:
                    result = await response.json(content_type=None)
                    if response.status != 200:
                        raise FrameApiError(str(result.get("error", response.status)))
                    if not isinstance(result, dict):
                        raise FrameApiError("Invalid JSON response")
                    return result
        except (ClientError, TimeoutError, ValueError) as exc:
            raise FrameApiError(str(exc)) from exc

    async def pair(self, code: str) -> dict:
        return await self.call("POST", "/api/v1/pair", {"code": code})

    async def status(self) -> dict:
        return await self.call("GET", "/api/v1/status")

    async def config(self) -> dict:
        return await self.call("GET", "/api/v1/config")

    async def update(self, values: dict) -> None:
        await self.call("POST", "/api/v1/config", values)

    async def notify(self, message: str, title: str = "Home Assistant", duration: int = 12) -> None:
        await self.call("POST", "/api/v1/notify", {
            "message": message, "title": title, "duration": duration
        })

    async def wake(self) -> None:
        await self.call("POST", "/api/v1/wake", {})
