# -*- coding: utf-8 -*-
"""设备通用下行 notify 协议（v1）。gateway 只转发 payload，业务由 action 区分。"""
from __future__ import annotations

from typing import Any, Dict, Optional

NOTIFY_MESSAGE_TYPE = "notify"
PROTOCOL_VERSION = 1

# action registry
ACTION_CAMERA_CAPTURE_AND_UPLOAD = "camera.capture_and_upload"
ACTION_CAMERA_START_LIVE = "camera.start_live"
ACTION_CAMERA_STOP_LIVE = "camera.stop_live"

# 云端 taskType（prepare/finalize 业务编排）
TASK_TYPE_PARENT_SNAPSHOT = "parent_snapshot"
TASK_TYPE_PARENT_LIVE = "parent_live"

# callback.mode
CALLBACK_MODE_HTTP_UPLOAD = "http_upload"


def build_notify_payload(
    *,
    action: str,
    request_id: str,
    params: Optional[Dict[str, Any]] = None,
    callback: Optional[Dict[str, Any]] = None,
    push: Optional[Dict[str, Any]] = None,
    task_type: Optional[str] = None,
    version: int = PROTOCOL_VERSION,
) -> Dict[str, Any]:
    payload: Dict[str, Any] = {
        "v": version,
        "action": action,
        "requestId": request_id,
    }
    if task_type:
        payload["taskType"] = task_type
    if params:
        payload["params"] = params
    if callback:
        payload["callback"] = callback
    if push:
        payload["push"] = push
    return payload


def build_parent_snapshot_notify_payload(
    *,
    request_id: str,
    upload_url: str,
    upload_token: str,
    max_width: int = 640,
    jpeg_quality: int = 80,
) -> Dict[str, Any]:
    """家长远程看娃：camera.capture_and_upload + http_upload callback。"""
    return build_notify_payload(
        action=ACTION_CAMERA_CAPTURE_AND_UPLOAD,
        request_id=request_id,
        task_type=TASK_TYPE_PARENT_SNAPSHOT,
        params={
            "maxWidth": max_width,
            "jpegQuality": jpeg_quality,
        },
        callback={
            "mode": CALLBACK_MODE_HTTP_UPLOAD,
            "url": upload_url,
            "token": upload_token,
            "headers": {
                "X-Snapshot-Token": upload_token,
            },
        },
    )


def build_parent_live_start_notify_payload(
    *,
    request_id: str,
    session_no: str,
    push_url: str,
    stream_key: str,
    app_name: str = "parent",
    max_duration_sec: int = 600,
    width: int = 640,
    height: int = 480,
    fps: int = 10,
    bitrate_kbps: int = 512,
) -> Dict[str, Any]:
    """家长远程监控：camera.start_live + RTMP push（腾讯云）。"""
    return build_notify_payload(
        action=ACTION_CAMERA_START_LIVE,
        request_id=request_id,
        task_type=TASK_TYPE_PARENT_LIVE,
        params={
            "sessionNo": session_no,
            "maxDurationSec": max_duration_sec,
            "video": {
                "width": width,
                "height": height,
                "fps": fps,
                "bitrateKbps": bitrate_kbps,
                "gop": 30,
                "codec": "h264",
            },
            "audio": {"enabled": False},
        },
        push={
            "mode": "rtmp",
            "url": push_url,
            "streamKey": stream_key,
            "appName": app_name,
        },
    )


def build_parent_live_stop_notify_payload(
    *,
    request_id: str,
    session_no: str,
    reason: str = "user_stop",
) -> Dict[str, Any]:
    return build_notify_payload(
        action=ACTION_CAMERA_STOP_LIVE,
        request_id=request_id,
        task_type=TASK_TYPE_PARENT_LIVE,
        params={
            "sessionNo": session_no,
            "reason": reason,
        },
    )
