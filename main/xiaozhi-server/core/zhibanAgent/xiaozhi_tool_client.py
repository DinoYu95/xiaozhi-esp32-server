# -*- coding: utf-8 -*-
"""
xiaozhi-server Zhiban 工具 internal API 客户端（供 zhiban-agent 复用或对照实现）。

环境变量建议：
  XIAOZHI_SERVER_URL=http://xiaozhi-server:8003
  MANAGER_API_SECRET=与智控台 server.secret 一致
"""
from __future__ import annotations

import json
from typing import Any, Dict, List, Optional

import httpx

DEFAULT_TIMEOUT = 35.0


class XiaozhiToolClient:
    def __init__(self, base_url: str, secret: str, timeout: float = DEFAULT_TIMEOUT):
        self.base_url = (base_url or "").rstrip("/")
        self.secret = secret or ""
        self.timeout = timeout

    def _headers(self) -> Dict[str, str]:
        return {
            "Authorization": f"Bearer {self.secret}",
            "Content-Type": "application/json",
        }

    def _get(self, path: str, params: Dict[str, Any]) -> Dict[str, Any]:
        if not self.base_url:
            raise RuntimeError("XIAOZHI_SERVER_URL 未配置")
        q = {k: v for k, v in params.items() if v is not None}
        with httpx.Client(timeout=self.timeout) as client:
            r = client.get(
                f"{self.base_url}{path}",
                params=q,
                headers=self._headers(),
            )
            r.raise_for_status()
            return r.json()

    def _post(self, path: str, body: Dict[str, Any]) -> Dict[str, Any]:
        if not self.base_url:
            raise RuntimeError("XIAOZHI_SERVER_URL 未配置")
        with httpx.Client(timeout=self.timeout) as client:
            r = client.post(
                f"{self.base_url}{path}",
                content=json.dumps(body, ensure_ascii=False),
                headers=self._headers(),
            )
            r.raise_for_status()
            return r.json()

    def device_mcp_status(
        self, *, device_id: Optional[str] = None, session_id: Optional[str] = None
    ) -> Dict[str, Any]:
        return self._get(
            "/internal/zhiban/device/mcp/status",
            {"device_id": device_id, "session_id": session_id},
        )

    def device_mcp_tools(
        self, *, device_id: Optional[str] = None, session_id: Optional[str] = None
    ) -> List[Dict[str, Any]]:
        data = self._get(
            "/internal/zhiban/device/mcp/tools",
            {"device_id": device_id, "session_id": session_id},
        )
        return data.get("tools") or []

    def device_mcp_call(
        self,
        tool_name: str,
        arguments: Optional[Dict[str, Any]] = None,
        *,
        device_id: Optional[str] = None,
        session_id: Optional[str] = None,
        timeout: int = 30,
    ) -> Dict[str, Any]:
        """调用设备 MCP。拍照返图请传 arguments={"mode": "image_only"}，响应 action=IMAGE 时读 image_base64。"""
        return self._post(
            "/internal/zhiban/device/mcp/call",
            {
                "device_id": device_id,
                "session_id": session_id,
                "tool_name": tool_name,
                "arguments": arguments or {},
                "timeout": timeout,
            },
        )

    def plugin_schemas(
        self, *, device_id: Optional[str] = None, session_id: Optional[str] = None
    ) -> Dict[str, Any]:
        return self._get(
            "/internal/zhiban/plugins/schemas",
            {"device_id": device_id, "session_id": session_id},
        )

    def plugin_execute(
        self,
        function_name: str,
        arguments: Optional[Dict[str, Any]] = None,
        *,
        device_id: Optional[str] = None,
        session_id: Optional[str] = None,
        timeout: int = 60,
    ) -> Dict[str, Any]:
        return self._post(
            "/internal/zhiban/plugins/execute",
            {
                "device_id": device_id,
                "session_id": session_id,
                "function_name": function_name,
                "arguments": arguments or {},
                "timeout": timeout,
            },
        )
