# -*- coding: utf-8 -*-
"""manager-api 成长星图：会话级 transcript 缓冲，连接结束时 batch 上报（LLM 判别在服务端）。"""

from __future__ import annotations

import json
import time
import uuid
from typing import Any, List, Dict

TAG = "growth_portrait_api_client"

# 会话分析含 LLM，超时略长；仅在连接关闭时同步调用
_SESSION_TIMEOUT_SEC = 60.0
_MIN_USER_TURNS = 1
_MIN_TURN_TEXT_LEN = 2
_MAX_TURNS = 40
_MAX_TURN_CHARS = 2000


def _client():
    from config.manage_api_client import ManageApiClient

    return ManageApiClient._instance


def growth_enabled(conn: Any) -> bool:
    cfg = getattr(conn, "config", None) or {}
    block = cfg.get("growth_portrait") or {}
    if isinstance(block, dict) and block.get("enabled") is False:
        return False
    return True


def should_track(conn: Any) -> bool:
    if not growth_enabled(conn):
        return False
    from core.utils.owner_dialogue_guard import should_attach_owner_child_context

    if not should_attach_owner_child_context(conn):
        return False
    return getattr(conn, "owner_child_id", None) is not None


def _normalize_turn_text(raw: str) -> str:
    s = (raw or "").strip()
    if not s:
        return ""
    if s.startswith("{") and s.endswith("}"):
        try:
            data = json.loads(s)
            if isinstance(data, dict):
                content = data.get("content")
                if content is not None and str(content).strip():
                    return str(content).strip()[:_MAX_TURN_CHARS]
        except (json.JSONDecodeError, TypeError, ValueError):
            pass
    return s[:_MAX_TURN_CHARS]


def _ensure_session(conn: Any) -> None:
    if getattr(conn, "growth_portrait_session_ref", None) is None:
        device_id = getattr(conn, "device_id", None) or "unknown"
        conn.growth_portrait_session_ref = f"{device_id}:{uuid.uuid4().hex[:12]}"
    if getattr(conn, "growth_portrait_turns", None) is None:
        conn.growth_portrait_turns = []


def _append_turn(conn: Any, role: str, text: str) -> None:
    normalized = _normalize_turn_text(text)
    if len(normalized) < _MIN_TURN_TEXT_LEN:
        return
    _ensure_session(conn)
    turns: List[Dict[str, str]] = conn.growth_portrait_turns
    if len(turns) >= _MAX_TURNS:
        turns.pop(0)
    turns.append({"role": role, "text": normalized})


def append_user_turn(conn: Any, text: str) -> None:
    """主孩子每轮发言：仅缓存，不上报。"""
    try:
        if not should_track(conn):
            return
        _append_turn(conn, "user", text)
    except Exception:
        pass


def append_assistant_turn(conn: Any, text: str) -> None:
    """机器人回复：与主孩子 session 成对缓存。"""
    try:
        turns = getattr(conn, "growth_portrait_turns", None)
        if not turns:
            return
        _append_turn(conn, "assistant", text)
    except Exception:
        pass


def _post_session(payload: dict) -> None:
    inst = _client()
    if not inst:
        return
    try:
        import httpx

        url = (inst.config.get("url") or "").rstrip("/")
        secret = (inst.config.get("secret") or "").strip()
        if not url or not secret:
            return
        with httpx.Client(
            base_url=url,
            headers={"Authorization": "Bearer " + secret, "Accept": "application/json"},
            timeout=_SESSION_TIMEOUT_SEC,
        ) as client:
            r = client.post("config/growth-portrait/evidence/session", json=payload)
            r.raise_for_status()
    except Exception as e:
        print(f"[{TAG}] POST session failed: {e}")


def flush_session(conn: Any) -> None:
    """连接关闭时提交整段 transcript；失败静默，不影响资源释放。"""
    try:
        turns: List[Dict[str, str]] = getattr(conn, "growth_portrait_turns", None) or []
        child_id = getattr(conn, "owner_child_id", None)
        if not turns or child_id is None:
            return
        user_count = sum(1 for t in turns if t.get("role") == "user")
        if user_count < _MIN_USER_TURNS:
            return
        source_ref = getattr(conn, "growth_portrait_session_ref", None) or f"session:{int(time.time())}"
        payload = {
            "childId": int(child_id),
            "sourceType": "conversation_session",
            "sourceRef": source_ref,
            "turns": turns,
        }
        _post_session(payload)
    except Exception as e:
        print(f"[{TAG}] flush_session failed: {e}")
    finally:
        try:
            conn.growth_portrait_turns = []
            conn.growth_portrait_session_ref = None
        except Exception:
            pass
