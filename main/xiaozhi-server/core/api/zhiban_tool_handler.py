# -*- coding: utf-8 -*-
"""
Zhiban 工具 internal HTTP：供 zhiban-agent 在 tool loop 中调用设备 MCP / Server Plugin。
鉴权与 /internal/parent/chat 一致（Bearer server.secret）。
"""
from __future__ import annotations

import json
from typing import Optional

from aiohttp import web

from config.logger import setup_logging
from core.zhibanAgent.connection_registry import resolve
from core.zhibanAgent import zhiban_tool_bridge

TAG = __name__
logger = setup_logging()


class ZhibanToolHandler:
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

    def _bad_request(self, detail: str) -> web.Response:
        return web.json_response({"detail": detail}, status=400)

    async def _parse_json(self, request: web.Request) -> Optional[dict]:
        try:
            body = await request.json()
        except Exception as e:
            self.logger.bind(tag=TAG).error("解析 JSON 失败: %s", e)
            return None
        return body if isinstance(body, dict) else None

    def _resolve_conn(self, request: web.Request, body: Optional[dict] = None):
        device_id = request.query.get("device_id") or request.query.get("device-id")
        session_id = request.query.get("session_id")
        if body:
            device_id = body.get("device_id") or device_id
            session_id = body.get("session_id") or session_id
        if not device_id and not session_id:
            return None, "device_id 或 session_id 必填其一"
        conn = resolve(device_id=device_id, session_id=session_id)
        if conn is None:
            return None, "设备会话不在线或未注册（需 ZhibanAgent 且 WebSocket 已连接）"
        return conn, None

    async def handle_options(self, request: web.Request) -> web.Response:
        return web.Response(
            status=204,
            headers={
                "Access-Control-Allow-Origin": "*",
                "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
                "Access-Control-Allow-Headers": "Authorization, Content-Type",
            },
        )

    async def handle_device_mcp_status(self, request: web.Request) -> web.Response:
        if not self._check_auth(request):
            return self._unauthorized()
        conn, err = self._resolve_conn(request)
        if err:
            return self._bad_request(err)
        try:
            status = await zhiban_tool_bridge.await_on_conn_loop(
                conn, zhiban_tool_bridge.get_device_mcp_status(conn)
            )
            return web.json_response(
                {
                    "device_id": getattr(conn, "device_id", None),
                    "session_id": getattr(conn, "session_id", None),
                    **status,
                }
            )
        except Exception as e:
            self.logger.bind(tag=TAG).error("device_mcp/status 失败: %s", e)
            return web.json_response({"detail": str(e)}, status=500)

    async def handle_device_mcp_tools(self, request: web.Request) -> web.Response:
        if not self._check_auth(request):
            return self._unauthorized()
        conn, err = self._resolve_conn(request)
        if err:
            return self._bad_request(err)
        try:
            tools = zhiban_tool_bridge.get_device_mcp_tool_schemas(conn)
            return web.json_response(
                {
                    "device_id": getattr(conn, "device_id", None),
                    "session_id": getattr(conn, "session_id", None),
                    "tools": tools,
                }
            )
        except Exception as e:
            self.logger.bind(tag=TAG).error("device_mcp/tools 失败: %s", e)
            return web.json_response({"detail": str(e)}, status=500)

    async def handle_device_mcp_call(self, request: web.Request) -> web.Response:
        if not self._check_auth(request):
            return self._unauthorized()
        body = await self._parse_json(request)
        if body is None:
            return self._bad_request("Invalid JSON")
        conn, err = self._resolve_conn(request, body)
        if err:
            return self._bad_request(err)
        tool_name = (body.get("tool_name") or body.get("name") or "").strip()
        if not tool_name:
            return self._bad_request("tool_name 不能为空")
        arguments = body.get("arguments") or {}
        if isinstance(arguments, str):
            try:
                arguments = json.loads(arguments) if arguments.strip() else {}
            except json.JSONDecodeError:
                return self._bad_request("arguments 不是合法 JSON")
        if not isinstance(arguments, dict):
            return self._bad_request("arguments 必须是 object")
        timeout = int(body.get("timeout") or 45)
        wait_result = body.get("wait_result", True)
        if isinstance(wait_result, str):
            wait_result = wait_result.strip().lower() not in ("0", "false", "no")
        loop_timeout = timeout + (30 if wait_result else 15)
        try:
            self.logger.bind(tag=TAG).info(
                "device_mcp/call 开始: tool=%s wait_result=%s timeout=%s loop_timeout=%s",
                tool_name,
                wait_result,
                timeout,
                loop_timeout,
            )
            result = await zhiban_tool_bridge.await_on_conn_loop(
                conn,
                zhiban_tool_bridge.execute_device_mcp(
                    conn,
                    tool_name,
                    arguments,
                    timeout=timeout,
                    wait_result=wait_result,
                ),
                timeout=loop_timeout,
            )
            return web.json_response(
                {
                    "device_id": getattr(conn, "device_id", None),
                    "session_id": getattr(conn, "session_id", None),
                    "tool_name": tool_name,
                    **result,
                }
            )
        except TimeoutError as e:
            self.logger.bind(tag=TAG).warning("device_mcp/call 超时: %s", e)
            return web.json_response(
                {
                    "ok": False,
                    "error": "timeout",
                    "detail": str(e),
                    "tool_name": tool_name,
                },
                status=504,
            )
        except Exception as e:
            self.logger.bind(tag=TAG).error("device_mcp/call 失败: %s", e)
            return web.json_response({"detail": str(e)}, status=500)

    async def handle_plugin_schemas(self, request: web.Request) -> web.Response:
        if not self._check_auth(request):
            return self._unauthorized()
        conn, err = self._resolve_conn(request)
        if err:
            return self._bad_request(err)
        try:
            tools = zhiban_tool_bridge.get_plugin_tool_schemas(conn)
            functions = zhiban_tool_bridge.get_plugin_function_names(conn)
            return web.json_response(
                {
                    "device_id": getattr(conn, "device_id", None),
                    "session_id": getattr(conn, "session_id", None),
                    "functions": functions,
                    "tools": tools,
                }
            )
        except Exception as e:
            self.logger.bind(tag=TAG).error("plugins/schemas 失败: %s", e)
            return web.json_response({"detail": str(e)}, status=500)

    async def handle_plugin_execute(self, request: web.Request) -> web.Response:
        if not self._check_auth(request):
            return self._unauthorized()
        body = await self._parse_json(request)
        if body is None:
            return self._bad_request("Invalid JSON")
        conn, err = self._resolve_conn(request, body)
        if err:
            return self._bad_request(err)
        function_name = (
            body.get("function_name") or body.get("name") or body.get("tool_name") or ""
        ).strip()
        if not function_name:
            return self._bad_request("function_name 不能为空")
        arguments = body.get("arguments") or {}
        if isinstance(arguments, str):
            try:
                arguments = json.loads(arguments) if arguments.strip() else {}
            except json.JSONDecodeError:
                return self._bad_request("arguments 不是合法 JSON")
        if not isinstance(arguments, dict):
            return self._bad_request("arguments 必须是 object")
        timeout = int(body.get("timeout") or 60)
        try:
            result = await zhiban_tool_bridge.await_on_conn_loop(
                conn,
                zhiban_tool_bridge.execute_server_plugin(
                    conn, function_name, arguments, timeout=timeout
                ),
                timeout=timeout + 10,
            )
            return web.json_response(
                {
                    "device_id": getattr(conn, "device_id", None),
                    "session_id": getattr(conn, "session_id", None),
                    "function_name": function_name,
                    **result,
                }
            )
        except Exception as e:
            self.logger.bind(tag=TAG).error("plugins/execute 失败: %s", e)
            return web.json_response({"detail": str(e)}, status=500)
