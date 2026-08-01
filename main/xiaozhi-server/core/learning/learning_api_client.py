# -*- coding: utf-8 -*-
"""manager-api 学习系统 internal 调用。"""

from __future__ import annotations

import time
import uuid
from typing import Any, Dict, Optional

TAG = "learning_api_client"


def _client():
    from config.manage_api_client import ManageApiClient

    return ManageApiClient._instance


def _post(path: str, payload: dict) -> bool:
    inst = _client()
    if not inst:
        return False
    try:
        import httpx

        url = (inst.config.get("url") or "").rstrip("/")
        secret = (inst.config.get("secret") or "").strip()
        if not url or not secret:
            return False
        with httpx.Client(
            base_url=url,
            headers={"Authorization": "Bearer " + secret, "Accept": "application/json"},
            timeout=float(inst.config.get("timeout", 30)),
        ) as client:
            r = client.post(path, json=payload)
            r.raise_for_status()
            body = r.json()
            return body.get("code") == 0
    except Exception as e:
        print(f"[{TAG}] POST {path} failed: {e}")
        return False


def learning_enabled(conn) -> bool:
    cfg = getattr(conn, "config", None) or {}
    block = cfg.get("learning") or {}
    if isinstance(block, dict) and block.get("enabled") is False:
        return False
    return True


def should_track(conn) -> bool:
    if not learning_enabled(conn):
        return False
    from core.utils.owner_dialogue_guard import should_attach_owner_child_context

    if not should_attach_owner_child_context(conn):
        return False
    return getattr(conn, "owner_child_id", None) is not None


def on_homework_enter(conn) -> None:
    if not should_track(conn):
        return
    if getattr(conn, "learning_session_uuid", None):
        return
    session_uuid = str(uuid.uuid4())
    conn.learning_session_uuid = session_uuid
    conn.learning_user_turn_count = 0
    conn.learning_photo_count = 0
    conn.learning_last_turn_ts = time.time()
    conn.learning_longest_silence = 0
    payload = {
        "sessionUuid": session_uuid,
        "deviceId": getattr(conn, "device_id", None),
        "childId": int(conn.owner_child_id),
        "startedAtMs": int(time.time() * 1000),
    }
    ok = _post("config/learning/session/start", payload)
    if not ok:
        conn.learning_session_uuid = None


def on_homework_exit(conn, end_reason: str = "manual") -> None:
    if not getattr(conn, "learning_session_uuid", None):
        return
    _flush_silence(conn)
    payload = {
        "sessionUuid": conn.learning_session_uuid,
        "endReason": end_reason,
        "endedAtMs": int(time.time() * 1000),
        "userTurnCount": int(getattr(conn, "learning_user_turn_count", 0) or 0),
        "photoCount": int(getattr(conn, "learning_photo_count", 0) or 0),
        "longestSilenceSec": int(getattr(conn, "learning_longest_silence", 0) or 0),
    }
    _post("config/learning/session/end", payload)
    conn.learning_session_uuid = None


def on_user_turn(conn, text: str) -> None:
    if not getattr(conn, "learning_session_uuid", None):
        return
    _flush_silence(conn)
    conn.learning_user_turn_count = int(getattr(conn, "learning_user_turn_count", 0) or 0) + 1
    conn.learning_last_turn_ts = time.time()
    seq = conn.learning_user_turn_count
    payload = {
        "sessionUuid": conn.learning_session_uuid,
        "text": (text or "")[:2000],
        "occurredAtMs": int(time.time() * 1000),
        "idempotencyKey": f"{conn.learning_session_uuid}:turn:{seq}",
    }
    _post("config/learning/session/turn", payload)


def on_photo_result(conn, vision_text: str, user_text: str = "", assistant_reply: str = "") -> None:
    if not getattr(conn, "learning_session_uuid", None):
        return
    conn.learning_photo_count = int(getattr(conn, "learning_photo_count", 0) or 0) + 1
    seq = conn.learning_photo_count
    payload = {
        "sessionUuid": conn.learning_session_uuid,
        "visionText": (vision_text or "")[:8000],
        "userQuestion": (user_text or "")[:2000],
        "assistantReply": (assistant_reply or "")[:4000],
        "occurredAtMs": int(time.time() * 1000),
        "idempotencyKey": f"{conn.learning_session_uuid}:photo:{seq}",
    }
    _post("config/learning/session/photo", payload)


def _flush_silence(conn) -> None:
    last = getattr(conn, "learning_last_turn_ts", None)
    if last is None:
        conn.learning_last_turn_ts = time.time()
        return
    gap = int(time.time() - float(last))
    if gap > int(getattr(conn, "learning_longest_silence", 0) or 0):
        conn.learning_longest_silence = gap
