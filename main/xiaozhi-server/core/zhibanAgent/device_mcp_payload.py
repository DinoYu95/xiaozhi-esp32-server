# -*- coding: utf-8 -*-
"""设备 MCP 工具结果整理，供 zhiban-agent internal API 使用。"""
from __future__ import annotations

from typing import Any, Dict, Optional


def flatten_device_mcp_response(payload: Dict[str, Any]) -> Dict[str, Any]:
    """
    将 action=IMAGE 的 result 字段展平到顶层，便于 zhiban-agent 直接读取 image_base64。
    日志中应避免打印完整 base64。
    """
    if not isinstance(payload, dict):
        return payload
    action = payload.get("action")
    result = payload.get("result")
    if action != "IMAGE" or not isinstance(result, dict):
        return payload

    image_b64 = result.get("image_base64")
    if image_b64:
        payload["image_base64"] = image_b64
    mime = result.get("mime_type")
    if mime:
        payload["mime_type"] = mime
    for key in ("width", "height", "capture_ms", "size_bytes", "mode"):
        if result.get(key) is not None:
            payload[key] = result[key]
    return payload


def device_mcp_call_ok(payload: Dict[str, Any]) -> bool:
    action = (payload or {}).get("action")
    return action not in ("ERROR", "NOTFOUND", None)


def image_payload_log_summary(payload: Dict[str, Any]) -> Dict[str, Optional[Any]]:
    """供日志使用，不含 base64 正文。"""
    result = payload.get("result") if isinstance(payload.get("result"), dict) else {}
    size = payload.get("size_bytes") or result.get("size_bytes")
    b64 = payload.get("image_base64") or result.get("image_base64")
    b64_len = len(b64) if isinstance(b64, str) else 0
    return {
        "action": payload.get("action"),
        "mime_type": payload.get("mime_type") or result.get("mime_type"),
        "width": payload.get("width") or result.get("width"),
        "height": payload.get("height") or result.get("height"),
        "size_bytes": size,
        "base64_len": b64_len,
        "capture_ms": payload.get("capture_ms") or result.get("capture_ms"),
    }
