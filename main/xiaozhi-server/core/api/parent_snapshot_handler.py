# -*- coding: utf-8 -*-
"""家长远程看娃 internal HTTP。"""
from __future__ import annotations

import time

from aiohttp import web

from config.logger import setup_logging
from core.api.parent_device_snapshot import capture_child_snapshot

TAG = __name__
logger = setup_logging()


class ParentSnapshotHandler:
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

    async def handle_capture(self, request: web.Request) -> web.Response:
        if not self._check_auth(request):
            return self._unauthorized()
        t0 = time.perf_counter()
        try:
            body = await request.json()
        except Exception:
            return web.json_response({"detail": "Invalid JSON"}, status=400)
        if not isinstance(body, dict):
            return web.json_response({"detail": "Invalid JSON"}, status=400)

        device_id = (body.get("device_id") or body.get("deviceId") or "").strip()
        request_id = (body.get("request_id") or body.get("requestId") or "").strip() or None
        self.logger.bind(tag=TAG).info(
            "parent/device-snapshot 收到请求 device={} requestId={} caller=zhiban",
            device_id,
            request_id or "-",
        )
        if not device_id:
            return web.json_response({"detail": "device_id 必填"}, status=400)
        timeout = int(body.get("timeout") or 20)

        result = await capture_child_snapshot(
            self.config, device_id, request_id=request_id, photo_timeout=timeout
        )
        self.logger.bind(tag=TAG).info(
            "parent/device-snapshot device={} code={} requestId={} elapsed_ms={:.1f}",
            device_id,
            result.code,
            result.request_id,
            (time.perf_counter() - t0) * 1000.0,
        )
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
