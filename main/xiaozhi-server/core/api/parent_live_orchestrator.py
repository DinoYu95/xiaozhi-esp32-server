# -*- coding: utf-8 -*-
"""家长远程实时监控：notify 编排。"""
from __future__ import annotations

import threading
import time
from dataclasses import dataclass
from typing import Any, Dict, Optional

from config.logger import setup_logging
from core.api.device_notify_protocol import (
    ACTION_CAMERA_START_LIVE,
    ACTION_CAMERA_STOP_LIVE,
    TASK_TYPE_PARENT_LIVE,
    build_parent_live_start_notify_payload,
    build_parent_live_stop_notify_payload,
)
from core.api.mqtt_gateway_client import check_device_mqtt_online, send_device_notify
from core.api.parent_device_snapshot import is_device_session_busy, is_device_snapshot_inflight

TAG = __name__
logger = setup_logging()

_LIVE_TTL_SEC = 7200
_live_lock = threading.RLock()
_live_by_device: Dict[str, Dict[str, Any]] = {}


@dataclass
class LiveOrchestrateResult:
    ok: bool
    code: str
    message: str
    session_no: Optional[str] = None

    def to_dict(self) -> Dict[str, Any]:
        return {
            "ok": self.ok,
            "code": self.code,
            "message": self.message,
            "sessionNo": self.session_no,
        }


def is_device_live_inflight(device_id: str) -> bool:
    _purge_live()
    with _live_lock:
        return device_id in _live_by_device


def _purge_live() -> None:
    now = time.time()
    with _live_lock:
        expired = [
            k
            for k, v in _live_by_device.items()
            if now - float(v.get("ts", 0)) > _LIVE_TTL_SEC
        ]
        for k in expired:
            _live_by_device.pop(k, None)


def _mark_live(device_id: str, session_no: str) -> None:
    with _live_lock:
        _live_by_device[device_id] = {"ts": time.time(), "session_no": session_no}


def _clear_live(device_id: str, session_no: str) -> None:
    with _live_lock:
        item = _live_by_device.get(device_id)
        if item and item.get("session_no") == session_no:
            _live_by_device.pop(device_id, None)


async def start_parent_live(config: dict, body: dict) -> LiveOrchestrateResult:
    device_id = (body.get("device_id") or body.get("deviceId") or "").strip()
    session_no = (body.get("session_no") or body.get("sessionNo") or "").strip()
    client_id = (body.get("client_id") or body.get("clientId") or "").strip()
    push = body.get("push") if isinstance(body.get("push"), dict) else {}

    if not device_id or not session_no:
        return LiveOrchestrateResult(False, "INVALID_REQUEST", "device_id、session_no 必填")

    push_url = (push.get("url") or "").strip()
    stream_key = (push.get("streamKey") or push.get("stream_key") or session_no).strip()
    app_name = (push.get("appName") or push.get("app_name") or "parent").strip()
    max_duration_sec = int(push.get("max_duration_sec") or push.get("maxDurationSec") or 600)
    video = push.get("video") if isinstance(push.get("video"), dict) else {}
    width = int(video.get("width") or 640)
    height = int(video.get("height") or 480)
    fps = int(video.get("fps") or 10)
    bitrate_kbps = int(video.get("bitrate_kbps") or video.get("bitrateKbps") or 512)

    if not push_url:
        return LiveOrchestrateResult(False, "INVALID_REQUEST", "push.url 必填", session_no)

    if is_device_live_inflight(device_id):
        return LiveOrchestrateResult(
            False,
            "LIVE_ALREADY_ACTIVE",
            "设备已有进行中的远程查看",
            session_no,
        )

    if is_device_snapshot_inflight(device_id):
        return LiveOrchestrateResult(
            False,
            "DEVICE_BUSY",
            "设备正在上传看娃快照，请稍后再试",
            session_no,
        )

    if is_device_session_busy(device_id):
        return LiveOrchestrateResult(
            False,
            "DEVICE_BUSY",
            "设备正在和孩子对话，暂时无法远程查看",
            session_no,
        )

    if not client_id:
        return LiveOrchestrateResult(False, "INVALID_REQUEST", "client_id 必填", session_no)

    online_check = await check_device_mqtt_online(
        config,
        client_id,
        log_context=f"live sessionNo={session_no} device={device_id}",
    )
    if not online_check.online:
        code = (
            "GATEWAY_UNAVAILABLE"
            if online_check.reason
            in ("mqtt_manager_api_not_configured", "gateway_status_unavailable")
            else "DEVICE_OFFLINE"
        )
        message = (
            "暂时无法连接设备网关，请稍后再试。"
            if code == "GATEWAY_UNAVAILABLE"
            else "设备离线中，暂时无法远程查看。"
        )
        return LiveOrchestrateResult(False, code, message, session_no)

    notify_payload = build_parent_live_start_notify_payload(
        request_id=session_no,
        session_no=session_no,
        push_url=push_url,
        stream_key=stream_key,
        app_name=app_name,
        max_duration_sec=max_duration_sec,
        width=width,
        height=height,
        fps=fps,
        bitrate_kbps=bitrate_kbps,
    )
    _mark_live(device_id, session_no)
    try:
        ok = await send_device_notify(config, client_id, notify_payload)
    except Exception as e:
        _clear_live(device_id, session_no)
        logger.bind(tag=TAG).exception(
            "live notify 下发失败 device={} action={} err={}",
            device_id,
            ACTION_CAMERA_START_LIVE,
            e,
        )
        return LiveOrchestrateResult(False, "NOTIFY_FAILED", "暂时无法远程查看", session_no)

    if not ok:
        _clear_live(device_id, session_no)
        return LiveOrchestrateResult(False, "NOTIFY_FAILED", "暂时无法远程查看", session_no)

    logger.bind(tag=TAG).info(
        "live notify 已下发 device={} clientId={} action={} taskType={} sessionNo={}",
        device_id,
        client_id,
        ACTION_CAMERA_START_LIVE,
        TASK_TYPE_PARENT_LIVE,
        session_no,
    )
    return LiveOrchestrateResult(True, "SUCCESS", "已通知设备开始推流", session_no)


async def stop_parent_live(config: dict, body: dict) -> LiveOrchestrateResult:
    device_id = (body.get("device_id") or body.get("deviceId") or "").strip()
    session_no = (body.get("session_no") or body.get("sessionNo") or "").strip()
    reason = (body.get("reason") or "user_stop").strip()
    client_id = (body.get("client_id") or body.get("clientId") or "").strip()

    if not device_id or not session_no:
        return LiveOrchestrateResult(False, "INVALID_REQUEST", "device_id、session_no 必填")

    if not client_id:
        # stop 时尽量仍下发；无 clientId 则仅清本地状态
        _clear_live(device_id, session_no)
        return LiveOrchestrateResult(True, "SUCCESS", "已清除本地 live 状态", session_no)

    notify_payload = build_parent_live_stop_notify_payload(
        request_id=session_no,
        session_no=session_no,
        reason=reason,
    )
    try:
        await send_device_notify(config, client_id, notify_payload)
    except Exception as e:
        logger.bind(tag=TAG).warning(
            "live stop notify 失败 device={} sessionNo={} err={}",
            device_id,
            session_no,
            e,
        )
    _clear_live(device_id, session_no)
    return LiveOrchestrateResult(True, "SUCCESS", "已通知设备停止推流", session_no)
