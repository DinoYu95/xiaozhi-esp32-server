# -*- coding: utf-8 -*-
"""家长远程看娃：通用 notify + 设备 HTTP 回传。"""
from __future__ import annotations

import threading
import time
import uuid
from dataclasses import dataclass
from typing import Any, Dict, Optional

from config.logger import setup_logging
from config.manage_api_client import prepare_parent_chat_snapshot
from core.api.device_notify_protocol import (
    ACTION_CAMERA_CAPTURE_AND_UPLOAD,
    TASK_TYPE_PARENT_SNAPSHOT,
    build_parent_snapshot_notify_payload,
)
from core.api.mqtt_gateway_client import check_device_mqtt_online, send_device_notify
from core.zhibanAgent.connection_registry import resolve

TAG = __name__
logger = setup_logging()

_INFLIGHT_TTL_SEC = 300
_inflight_lock = threading.RLock()
_inflight_by_device: Dict[str, Dict[str, Any]] = {}


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


def _purge_inflight() -> None:
    now = time.time()
    with _inflight_lock:
        expired = [
            k
            for k, v in _inflight_by_device.items()
            if now - float(v.get("ts", 0)) > _INFLIGHT_TTL_SEC
        ]
        for k in expired:
            _inflight_by_device.pop(k, None)


def _mark_inflight(device_id: str, request_id: str) -> None:
    with _inflight_lock:
        _inflight_by_device[device_id] = {"ts": time.time(), "request_id": request_id}


def _clear_inflight(device_id: str, request_id: str) -> None:
    with _inflight_lock:
        item = _inflight_by_device.get(device_id)
        if item and item.get("request_id") == request_id:
            _inflight_by_device.pop(device_id, None)


def is_device_snapshot_inflight(device_id: str) -> bool:
    _purge_inflight()
    with _inflight_lock:
        return device_id in _inflight_by_device


def is_device_session_busy(device_id: str) -> bool:
    """若 WS 桥仍在，则沿用会话忙标志；否则仅看本链路 in-flight。"""
    if is_device_snapshot_inflight(device_id):
        return True
    conn = resolve(device_id=device_id)
    if conn is None:
        return False
    chat_inflight = int(getattr(conn, "_chat_inflight", 0) or 0) > 0
    speaking = bool(getattr(conn, "client_is_speaking", False))
    snapshot_busy = bool(getattr(conn, "_parent_snapshot_in_progress", False))
    return chat_inflight or speaking or snapshot_busy


async def capture_child_snapshot(
    config: dict,
    device_id: str,
    *,
    request_id: Optional[str] = None,
    photo_timeout: int = 20,
) -> SnapshotCaptureResult:
    """MQTT 在线检查 → prepare → notify(camera.capture_and_upload)。"""
    t_total = time.perf_counter()
    _purge_inflight()
    rid = (request_id or "").strip() or ("snap_%s" % uuid.uuid4().hex[:16])
    device_id = (device_id or "").strip()
    if not device_id:
        return SnapshotCaptureResult(
            ok=False,
            code="INVALID_DEVICE",
            message="设备信息无效，暂时无法远程看画面。",
            request_id=rid,
        )

    t0 = time.perf_counter()
    prepare = await prepare_parent_chat_snapshot(device_id, rid, config)
    logger.bind(tag=TAG).info(
        "看娃阶段 prepare 完成 device={} requestId={} elapsed_ms={:.1f} ok={}",
        device_id,
        rid,
        (time.perf_counter() - t0) * 1000.0,
        bool(prepare),
    )
    if not prepare:
        return SnapshotCaptureResult(
            ok=False,
            code="PREPARE_FAILED",
            message="暂时无法发起远程看画面，请稍后再试。",
            request_id=rid,
        )

    client_id = (prepare.get("clientId") or "").strip()
    upload_url = (prepare.get("uploadUrl") or "").strip()
    upload_token = (prepare.get("uploadToken") or "").strip()
    if not client_id or not upload_url or not upload_token:
        return SnapshotCaptureResult(
            ok=False,
            code="PREPARE_FAILED",
            message="远程看画面配置异常，请稍后再试。",
            request_id=rid,
        )

    logger.bind(tag=TAG).info(
        "看娃 prepare 成功 device={} requestId={} clientId={} taskType={} uploadUrl={}",
        device_id,
        rid,
        client_id,
        prepare.get("taskType") or TASK_TYPE_PARENT_SNAPSHOT,
        upload_url[:80] + "..." if len(upload_url) > 80 else upload_url,
    )
    if "//web:" in upload_url or "://web/" in upload_url:
        logger.bind(tag=TAG).warning(
            "看娃 uploadUrl 含 Docker 内部主机 web，ESP32 无法 HTTP 上传 device={} requestId={} uploadUrl={}",
            device_id,
            rid,
            upload_url,
        )

    t0 = time.perf_counter()
    online_check = await check_device_mqtt_online(
        config,
        client_id,
        log_context=f"snapshot requestId={rid} device={device_id}",
    )
    logger.bind(tag=TAG).info(
        "看娃阶段 mqtt_online 完成 device={} requestId={} elapsed_ms={:.1f} online={} reason={}",
        device_id,
        rid,
        (time.perf_counter() - t0) * 1000.0,
        online_check.online,
        online_check.reason,
    )
    if not online_check.online:
        if online_check.reason in (
            "mqtt_manager_api_not_configured",
            "gateway_status_unavailable",
        ):
            code = "GATEWAY_UNAVAILABLE"
            message = "暂时无法连接设备网关，请稍后再试。"
        else:
            code = "DEVICE_OFFLINE"
            message = "设备离线中，暂时看不到画面。请确认设备已开机并联网。"
        logger.bind(tag=TAG).warning(
            "看娃在线检查未通过 device={} requestId={} code={} check={}",
            device_id,
            rid,
            code,
            online_check.summary(),
        )
        return SnapshotCaptureResult(
            ok=False,
            code=code,
            message=message,
            request_id=rid,
        )

    if is_device_session_busy(device_id):
        logger.bind(tag=TAG).warning(
            "看娃设备忙 device={} requestId={} clientId={}",
            device_id,
            rid,
            client_id,
        )
        return SnapshotCaptureResult(
            ok=False,
            code="DEVICE_BUSY",
            message="设备正在和孩子对话，暂时无法远程看画面，请稍后再试。",
            request_id=rid,
        )

    notify_payload = build_parent_snapshot_notify_payload(
        request_id=rid,
        upload_url=upload_url,
        upload_token=upload_token,
        max_width=640,
        jpeg_quality=80,
    )
    _mark_inflight(device_id, rid)
    try:
        t0 = time.perf_counter()
        ok = await send_device_notify(config, client_id, notify_payload)
        logger.bind(tag=TAG).info(
            "看娃阶段 notify 完成 device={} requestId={} elapsed_ms={:.1f} ok={}",
            device_id,
            rid,
            (time.perf_counter() - t0) * 1000.0,
            ok,
        )
    except Exception as e:
        _clear_inflight(device_id, rid)
        logger.bind(tag=TAG).exception(
            "notify 下发失败 device={} action={} err={}",
            device_id,
            ACTION_CAMERA_CAPTURE_AND_UPLOAD,
            e,
        )
        return SnapshotCaptureResult(
            ok=False,
            code="NOTIFY_FAILED",
            message="暂时无法远程看画面，请稍后再试。",
            request_id=rid,
        )

    if not ok:
        _clear_inflight(device_id, rid)
        return SnapshotCaptureResult(
            ok=False,
            code="NOTIFY_FAILED",
            message="暂时无法远程看画面，请稍后再试。",
            request_id=rid,
        )

    logger.bind(tag=TAG).info(
        "notify 已下发 device={} clientId={} action={} taskType={} requestId={} total_elapsed_ms={:.1f}",
        device_id,
        client_id,
        ACTION_CAMERA_CAPTURE_AND_UPLOAD,
        TASK_TYPE_PARENT_SNAPSHOT,
        rid,
        (time.perf_counter() - t_total) * 1000.0,
    )
    return SnapshotCaptureResult(
        ok=True,
        code="SUCCESS",
        message="好的，我拍到画面啦，正在传给你。",
        request_id=rid,
        mime_type="image/jpeg",
    )
