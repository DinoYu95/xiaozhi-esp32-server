# -*- coding: utf-8 -*-
"""家长远程监控 internal HTTP。"""
from __future__ import annotations

import time

from aiohttp import web

from config.logger import setup_logging
from core.api.parent_live_orchestrator import start_parent_live, stop_parent_live

TAG = __name__
logger = setup_logging()


class ParentLiveHandler:
    def __init__(self, config: dict):
        self.config = config
        self.logger = logger
        self._secret = config.get("manager-api", {}).get("secret", "")

    def _check_auth(self, request: web.Request) -> bool:
        auth = request.headers.get("Authorization")
        if not auth or not auth.startswith("Bearer "):
            return False
        token = auth[7:].strip()
        return bool(self._secret) and token == self._secret

    def _unauthorized(self) -> web.Response:
        return web.json_response({"detail": "Unauthorized"}, status=401)

    async def handle_start(self, request: web.Request) -> web.Response:
        if not self._check_auth(request):
            return self._unauthorized()
        t0 = time.perf_counter()
        try:
            body = await request.json()
        except Exception:
            return web.json_response({"detail": "Invalid JSON"}, status=400)
        if not isinstance(body, dict):
            return web.json_response({"detail": "Invalid JSON"}, status=400)

        result = await start_parent_live(self.config, body)
        self.logger.bind(tag=TAG).info(
            "parent/live/start device={} sessionNo={} ok={} code={} elapsed_ms={:.1f}",
            body.get("device_id") or body.get("deviceId"),
            body.get("session_no") or body.get("sessionNo"),
            result.ok,
            result.code,
            (time.perf_counter() - t0) * 1000.0,
        )
        return web.json_response(result.to_dict())

    async def handle_stop(self, request: web.Request) -> web.Response:
        if not self._check_auth(request):
            return self._unauthorized()
        try:
            body = await request.json()
        except Exception:
            return web.json_response({"detail": "Invalid JSON"}, status=400)
        if not isinstance(body, dict):
            return web.json_response({"detail": "Invalid JSON"}, status=400)

        result = await stop_parent_live(self.config, body)
        return web.json_response(result.to_dict())

    async def handle_options(self, request: web.Request) -> web.Response:
        return web.Response(
            status=204,
            headers={
                "Access-Control-Allow-Origin": "*",
                "Access-Control-Allow-Methods": "POST, OPTIONS",
                "Access-Control-Allow-Headers": "Authorization, Content-Type",
            },
        )
