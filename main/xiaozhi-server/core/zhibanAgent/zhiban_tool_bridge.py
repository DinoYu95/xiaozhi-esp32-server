# -*- coding: utf-8 -*-
"""
Zhiban 工具桥接：调用现有 Device MCP / Server Plugin 实现，不修改 device_mcp 目录内代码。
"""
from __future__ import annotations

import asyncio
import json
from typing import Any, Dict, List, Optional

from config.logger import setup_logging
from plugins_func.register import Action, ActionResponse
from core.providers.tools.device_mcp.mcp_executor import DeviceMCPExecutor
from core.providers.tools.server_plugins.plugin_executor import ServerPluginExecutor

TAG = __name__
logger = setup_logging()


def action_response_to_dict(response) -> Dict[str, Any]:
    if response is None:
        return {"action": "ERROR", "result": None, "response": "无响应"}
    action = getattr(response, "action", Action.ERROR)
    action_name = action.name if isinstance(action, Action) else str(action)
    return {
        "action": action_name,
        "result": getattr(response, "result", None),
        "response": getattr(response, "response", None),
    }


async def _device_mcp_ready(conn) -> bool:
    mcp_client = getattr(conn, "mcp_client", None)
    if not mcp_client:
        return False
    return await mcp_client.is_ready()


async def get_device_mcp_status(conn) -> Dict[str, Any]:
    mcp_client = getattr(conn, "mcp_client", None)
    ready = await _device_mcp_ready(conn)
    tool_names: List[str] = []
    if mcp_client and ready:
        for tool in mcp_client.get_available_tools():
            func_def = (tool or {}).get("function") or {}
            name = func_def.get("name")
            if name:
                tool_names.append(name)
    return {
        "ready": ready,
        "has_mcp_client": mcp_client is not None,
        "tool_names": tool_names,
        "tool_count": len(tool_names),
    }


def get_device_mcp_tool_schemas(conn) -> List[Dict[str, Any]]:
    executor = DeviceMCPExecutor(conn)
    tools = executor.get_tools()
    return [t.description for t in tools.values()]


def get_plugin_tool_schemas(conn) -> List[Dict[str, Any]]:
    executor = ServerPluginExecutor(conn)
    tools = executor.get_tools()
    return [t.description for t in tools.values()]


def get_plugin_function_names(conn) -> List[str]:
    intent_cfg = (conn.config.get("Intent") or {}).get(
        conn.config.get("selected_module", {}).get("Intent", ""), {}
    )
    functions = intent_cfg.get("functions") or []
    if not isinstance(functions, list):
        try:
            functions = list(functions)
        except TypeError:
            functions = []
    necessary = ["handle_exit_intent", "get_lunar"]
    return list(dict.fromkeys(list(necessary) + list(functions)))


async def execute_device_mcp(
    conn, tool_name: str, arguments: Optional[Dict[str, Any]] = None, timeout: int = 30
) -> Dict[str, Any]:
    executor = DeviceMCPExecutor(conn)
    if not executor.has_tool(tool_name):
        return {
            "ok": False,
            "error": f"设备 MCP 工具不存在: {tool_name}",
            **action_response_to_dict(
                ActionResponse(
                    action=Action.NOTFOUND,
                    response=f"工具 {tool_name} 不存在",
                )
            ),
        }
    try:
        response = await asyncio.wait_for(
            executor.execute(conn, tool_name, arguments or {}),
            timeout=timeout,
        )
        payload = action_response_to_dict(response)
        payload["ok"] = payload.get("action") != "ERROR"
        return payload
    except asyncio.TimeoutError:
        return {
            "ok": False,
            "action": "ERROR",
            "result": None,
            "response": "设备 MCP 工具调用超时",
            "error": "timeout",
        }
    except Exception as e:
        logger.bind(tag=TAG).error(f"execute_device_mcp 失败: {e}")
        return {
            "ok": False,
            "action": "ERROR",
            "result": None,
            "response": str(e),
            "error": str(e),
        }


async def execute_server_plugin(
    conn, function_name: str, arguments: Optional[Dict[str, Any]] = None, timeout: int = 60
) -> Dict[str, Any]:
    executor = ServerPluginExecutor(conn)
    if not executor.has_tool(function_name):
        return {
            "ok": False,
            "error": f"插件不存在: {function_name}",
            "action": "NOTFOUND",
            "result": None,
            "response": f"插件 {function_name} 不存在",
        }
    try:
        response = await asyncio.wait_for(
            executor.execute(conn, function_name, arguments or {}),
            timeout=timeout,
        )
        payload = action_response_to_dict(response)
        payload["ok"] = payload.get("action") not in ("ERROR", "NOTFOUND")
        if payload.get("action") == "NONE":
            payload["meta"] = {"tts_side_effect": True}
        return payload
    except asyncio.TimeoutError:
        return {
            "ok": False,
            "action": "ERROR",
            "result": None,
            "response": "插件执行超时",
            "error": "timeout",
        }
    except Exception as e:
        logger.bind(tag=TAG).error(f"execute_server_plugin 失败: {e}")
        return {
            "ok": False,
            "action": "ERROR",
            "result": None,
            "response": str(e),
            "error": str(e),
        }


def run_on_conn_loop(conn, coro, timeout: float = 65):
    """在设备连接所属 asyncio 事件循环上执行协程（供 aiohttp handler 调用）。"""
    loop = getattr(conn, "loop", None)
    if loop is None:
        raise RuntimeError("连接无 event loop")
    if loop.is_running():
        future = asyncio.run_coroutine_threadsafe(coro, loop)
        return future.result(timeout=timeout)
    return loop.run_until_complete(coro)


async def build_environment_tool_context(conn) -> Dict[str, Any]:
    """供 environment_context 附加的 Zhiban 工具快照。"""
    device_mcp = await get_device_mcp_status(conn)
    return {
        "device_mcp": device_mcp,
        "plugin_functions": get_plugin_function_names(conn),
        "xiaozhi_session_id": getattr(conn, "session_id", None),
    }
