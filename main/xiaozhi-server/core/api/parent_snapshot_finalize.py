# -*- coding: utf-8 -*-
"""家长远程看娃：等待设备 HTTP 上传并 WS 通知。"""
from __future__ import annotations

import asyncio
import json
from typing import Any, Dict, Optional

from aiohttp import web

from config.logger import setup_logging
from config.manage_api_client import finalize_parent_chat_snapshot, get_parent_chat_snapshot_status
from core.api.parent_device_snapshot import _clear_inflight

TAG = __name__
logger = setup_logging()

_WAIT_TIMEOUT_SEC = 90
_POLL_INTERVAL_SEC = 1.0


async def finalize_parent_snapshot_on_ws(
    ws: web.WebSocketResponse,
    config: dict,
    parent_user_id: int,
    child_id: int,
    device_id: str,
    request_id: str,
    assistant_message_id: int,
) -> None:
    deadline = asyncio.get_event_loop().time() + _WAIT_TIMEOUT_SEC
    last_status = "waiting"
    width: Optional[int] = None
    height: Optional[int] = None

    while asyncio.get_event_loop().time() < deadline:
        status_data = await get_parent_chat_snapshot_status(request_id)
        if status_data:
            last_status = (status_data.get("status") or "waiting").strip()
            if last_status == "uploaded":
                width = status_data.get("width")
                height = status_data.get("height")
                break
            if last_status in ("expired", "not_found"):
                break
        await asyncio.sleep(_POLL_INTERVAL_SEC)

    if last_status != "uploaded":
        code = "UPLOAD_TIMEOUT" if last_status == "waiting" else last_status.upper()
        msg = (
            "拍照超时了，请稍后再试。"
            if last_status == "waiting"
            else "画面已过期，请重新发起查看。"
        )
        await ws.send_str(
            json.dumps(
                {
                    "type": "snapshot_failed",
                    "requestId": request_id,
                    "code": code,
                    "message": msg,
                },
                ensure_ascii=False,
            )
        )
        _clear_inflight(device_id, request_id)
        return

    upload = await finalize_parent_chat_snapshot(
        parent_user_id,
        child_id,
        assistant_message_id,
        request_id,
    )
    _clear_inflight(device_id, request_id)
    if not upload:
        await ws.send_str(
            json.dumps(
                {
                    "type": "snapshot_failed",
                    "requestId": request_id,
                    "code": "UPLOAD_FAILED",
                    "message": "图片上传失败，请稍后再试。",
                },
                ensure_ascii=False,
            )
        )
        return

    await ws.send_str(
        json.dumps(
            {
                "type": "snapshot_ready",
                "requestId": request_id,
                "messageId": upload.get("messageId") or assistant_message_id,
                "image": {
                    "objectKey": upload.get("objectKey"),
                    "accessUrl": upload.get("accessUrl"),
                    "width": width,
                    "height": height,
                },
            },
            ensure_ascii=False,
        )
    )
