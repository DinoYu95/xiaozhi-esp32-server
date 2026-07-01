# -*- coding: utf-8 -*-
"""ZhibanAgent 模式下连接生命周期挂钩（注册表 / environment_context）。"""
from __future__ import annotations

from typing import Any, Dict

from config.logger import setup_logging
from core.zhibanAgent.connection_registry import register, unregister
from core.zhibanAgent import zhiban_tool_bridge

TAG = __name__
logger = setup_logging()


def is_zhiban_connection(conn) -> bool:
    if conn is None or getattr(conn, "llm", None) is None:
        return False
    mod = getattr(type(conn.llm), "__module__", "") or ""
    return "ZhibanAgent" in mod


def maybe_register_connection(conn) -> None:
    if not is_zhiban_connection(conn):
        return
    if not getattr(conn, "device_id", None):
        return
    register(conn)
    logger.bind(tag=TAG).debug(
        "Zhiban 连接已注册 tool bridge: device_id=%s session_id=%s",
        conn.device_id,
        getattr(conn, "session_id", None),
    )


def maybe_unregister_connection(conn) -> None:
    if not is_zhiban_connection(conn):
        return
    unregister(conn)
    logger.bind(tag=TAG).debug(
        "Zhiban 连接已注销 tool bridge: device_id=%s",
        getattr(conn, "device_id", None),
    )


def attach_tool_context_to_environment(conn, ctx: Dict[str, Any]) -> None:
    """在 _build_environment_context 末尾调用，仅 Zhiban 模式附加工具信息。"""
    if not is_zhiban_connection(conn):
        return
    try:
        loop = getattr(conn, "loop", None)
        if loop and loop.is_running():
            future = __import__("asyncio").run_coroutine_threadsafe(
                zhiban_tool_bridge.build_environment_tool_context(conn), loop
            )
            tool_ctx = future.result(timeout=3)
        else:
            tool_ctx = {"device_mcp": {"ready": False}, "plugin_functions": []}
    except Exception as e:
        logger.bind(tag=TAG).warning(f"附加 Zhiban tool context 失败: {e}")
        tool_ctx = {
            "device_mcp": {"ready": False, "has_mcp_client": bool(getattr(conn, "mcp_client", None))},
            "plugin_functions": zhiban_tool_bridge.get_plugin_function_names(conn),
            "xiaozhi_session_id": getattr(conn, "session_id", None),
        }
    ctx["device_mcp"] = tool_ctx.get("device_mcp")
    ctx["plugin_functions"] = tool_ctx.get("plugin_functions")
    ctx["xiaozhi_session_id"] = tool_ctx.get("xiaozhi_session_id") or getattr(
        conn, "session_id", None
    )
