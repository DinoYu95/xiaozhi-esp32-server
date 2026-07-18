# -*- coding: utf-8 -*-
"""家长远程看娃：OSS 上传与 WS 通知。"""
from __future__ import annotations

import json
from typing import Any, Dict, Optional

from aiohttp import web

from config.logger import setup_logging
from config.manage_api_client import upload_parent_chat_snapshot
from core.api.parent_device_snapshot import pop_snapshot_image

TAG = __name__
logger = setup_logging()


async def finalize_parent_snapshot_on_ws(
    ws: web.WebSocketResponse,
    parent_user_id: int,
    child_id: int,
    request_id: str,
    assistant_message_id: int,
) -> None:
    cached = pop_snapshot_image(request_id)
    if not cached:
        await ws.send_str(
            json.dumps(
                {
                    "type": "snapshot_failed",
                    "requestId": request_id,
                    "code": "CACHE_MISS",
                    "message": "画面已过期，请重新发起查看。",
                },
                ensure_ascii=False,
            )
        )
        return

    upload = await upload_parent_chat_snapshot(
        parent_user_id,
        child_id,
        assistant_message_id,
        cached.get("image_base64") or "",
        snapshot_request_id=request_id,
        mime_type=cached.get("mime_type"),
    )
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
                    "width": cached.get("width"),
                    "height": cached.get("height"),
                },
            },
            ensure_ascii=False,
        )
    )
