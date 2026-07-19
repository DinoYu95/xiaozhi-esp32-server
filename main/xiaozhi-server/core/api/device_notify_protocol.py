# -*- coding: utf-8 -*-
"""设备通用下行 notify 协议（v1）。gateway 只转发 payload，业务由 action 区分。"""
from __future__ import annotations

from typing import Any, Dict, Optional

NOTIFY_MESSAGE_TYPE = "notify"
PROTOCOL_VERSION = 1

# action registry
ACTION_CAMERA_CAPTURE_AND_UPLOAD = "camera.capture_and_upload"

# 云端 taskType（prepare/finalize 业务编排）
TASK_TYPE_PARENT_SNAPSHOT = "parent_snapshot"

# callback.mode
CALLBACK_MODE_HTTP_UPLOAD = "http_upload"


def build_notify_payload(
    *,
    action: str,
    request_id: str,
    params: Optional[Dict[str, Any]] = None,
    callback: Optional[Dict[str, Any]] = None,
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
