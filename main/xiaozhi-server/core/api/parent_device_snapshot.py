# -*- coding: utf-8 -*-
"""家长远程看娃：设备拍照、缓存、忙闲检测。"""
from __future__ import annotations

import threading
import time
import uuid
from dataclasses import dataclass
from typing import Any, Dict, Optional

from config.logger import setup_logging
from core.zhibanAgent.connection_registry import resolve
from core.zhibanAgent.device_mcp_payload import extract_image_payload
from core.zhibanAgent import zhiban_tool_bridge

TAG = __name__
logger = setup_logging()

_CACHE_TTL_SEC = 300
_cache_lock = threading.RLock()
_snapshot_cache: Dict[str, Dict[str, Any]] = {}


@dataclass
class SnapshotCaptureResult:
    ok: bool
    code: str
    message: str
    request_id: Optional[str] = None
    mime_type: Optional[str] = None
    width: Optional[int] = None
    height: Optional[int] = None

    def to_dict(self) -> Dict[str, Any]:
        out = {
            "ok": self.ok,
            "code": self.code,
            "message": self.message,
        }
        if self.request_id:
            out["requestId"] = self.request_id
        if self.mime_type:
            out["mimeType"] = self.mime_type
        if self.width is not None:
            out["width"] = self.width
        if self.height is not None:
            out["height"] = self.height
        return out


def _purge_expired_cache() -> None:
    now = time.time()
    with _cache_lock:
        expired = [k for k, v in _snapshot_cache.items() if now - float(v.get("ts", 0)) > _CACHE_TTL_SEC]
        for k in expired:
            _snapshot_cache.pop(k, None)


def pop_snapshot_image(request_id: str) -> Optional[Dict[str, Any]]:
    _purge_expired_cache()
    with _cache_lock:
        item = _snapshot_cache.pop(request_id, None)
    return item


def peek_snapshot_image(request_id: str) -> Optional[Dict[str, Any]]:
    _purge_expired_cache()
    with _cache_lock:
        item = _snapshot_cache.get(request_id)
    return dict(item) if item else None


def is_device_session_busy(conn) -> bool:
    chat_inflight = int(getattr(conn, "_chat_inflight", 0) or 0) > 0
    speaking = bool(getattr(conn, "client_is_speaking", False))
    snapshot_busy = bool(getattr(conn, "_parent_snapshot_in_progress", False))
    return chat_inflight or speaking or snapshot_busy


def _pick_photo_tool(conn) -> str:
    try:
        tools = zhiban_tool_bridge.get_device_mcp_tool_schemas(conn) or []
    except Exception:
        tools = []
    for t in tools:
        name = (t.get("name") or "").strip()
        if not name:
            continue
        low = name.lower().replace(".", "_")
        if "take_photo" in low or ("camera" in low and "photo" in low):
            return name
    return "self_camera_take_photo"


async def capture_child_snapshot(
    device_id: str,
    *,
    request_id: Optional[str] = None,
    photo_timeout: int = 20,
) -> SnapshotCaptureResult:
    """严格在线：设备 WS 已注册且当前不在对话/拍照中。"""
    _purge_expired_cache()
    rid = (request_id or "").strip() or ("snap_%s" % uuid.uuid4().hex[:16])
    conn = resolve(device_id=device_id)
    if conn is None:
        return SnapshotCaptureResult(
            ok=False,
            code="DEVICE_OFFLINE",
            message="设备离线中，暂时看不到画面。请确认设备已开机并联网。",
            request_id=rid,
        )
    if is_device_session_busy(conn):
        return SnapshotCaptureResult(
            ok=False,
            code="DEVICE_BUSY",
            message="设备正在和孩子对话，暂时无法远程看画面，请稍后再试。",
            request_id=rid,
        )

    tool_name = _pick_photo_tool(conn)
    args = {"mode": "image_only", "max_width": 640, "jpeg_quality": 80}
    loop_timeout = photo_timeout + 30
    conn._parent_snapshot_in_progress = True
    try:
        result = await zhiban_tool_bridge.await_on_conn_loop(
            conn,
            zhiban_tool_bridge.execute_device_mcp(
                conn,
                tool_name,
                args,
                timeout=photo_timeout,
                wait_result=True,
            ),
            timeout=loop_timeout,
        )
    except TimeoutError:
        return SnapshotCaptureResult(
            ok=False,
            code="MCP_TIMEOUT",
            message="拍照超时了，请稍后再试。",
            request_id=rid,
        )
    except Exception as e:
        logger.bind(tag=TAG).exception("远程看娃拍照失败 device=%s: %s", device_id, e)
        return SnapshotCaptureResult(
            ok=False,
            code="MCP_FAILED",
            message="拍照失败了，请稍后再试。",
            request_id=rid,
        )
    finally:
        conn._parent_snapshot_in_progress = False

    image = extract_image_payload(result if isinstance(result, dict) else {})
    if not image or not image.get("image_base64"):
        return SnapshotCaptureResult(
            ok=False,
            code="NO_IMAGE",
            message="这次没拍到画面，请稍后再试。",
            request_id=rid,
        )

    with _cache_lock:
        _snapshot_cache[rid] = {
            "ts": time.time(),
            "device_id": device_id,
            "image_base64": image.get("image_base64"),
            "mime_type": image.get("mime_type") or "image/jpeg",
            "width": image.get("width"),
            "height": image.get("height"),
        }

    return SnapshotCaptureResult(
        ok=True,
        code="SUCCESS",
        message="好的，我拍到画面啦，正在传给你。",
        request_id=rid,
        mime_type=image.get("mime_type") or "image/jpeg",
        width=image.get("width"),
        height=image.get("height"),
    )
