# -*- coding: utf-8 -*-
"""
作业辅导模式：会话级状态，由唤醒词进入/退出。
进入后 active_mode=homework_tutor 经 environment_context 传给 zhiban-agent。
"""
from __future__ import annotations

import json
import time
from typing import Optional, Tuple

from core.utils.util import remove_punctuation_and_length

MODE_ID = "homework_tutor"

# 默认 30 分钟无操作自动退出
DEFAULT_IDLE_TIMEOUT_SEC = 30 * 60

ENTER_PHRASES = (
    "进入作业辅导",
    "作业辅导模式",
    "开始作业辅导",
    "帮我辅导作业",
    "我要写作业了",
    "进入辅导模式",
)

EXIT_PHRASES = (
    "退出作业辅导",
    "结束作业辅导",
    "结束辅导",
    "不辅导了",
    "退出辅导",
)

ENTER_REPLY = (
    "好的，我们进入作业辅导模式啦！有不会的题目可以问我，"
    "也可以说「这道题怎么做」，我会先帮你看题目。"
    "辅导结束后记得说「退出作业辅导」哦。"
)

EXIT_REPLY = "作业辅导结束啦，辛苦你了！想聊天或听故事随时叫我哦。"

ALREADY_IN_MODE_REPLY = "我们已经在作业辅导模式里啦，有不会的题目直接问我就行。"

NOT_IN_MODE_REPLY = "我们现在不在作业辅导模式哦，想说「进入作业辅导」就可以开始。"

TIMEOUT_REPLY = "作业辅导已经有一段时间啦，我先退出辅导模式。下次需要再说「进入作业辅导」哦。"

# 孩子说「好了」后、设备拍照前短暂反馈
PHOTO_CAPTURE_START_REPLY = "好，我来拍啦，稍等一下哦。"

# 孩子说这些才真的拍照
PHOTO_READY_PHRASES = (
    "好了",
    "准备好了",
    "可以了",
    "帮我拍照",
    "拍吧",
    "可以拍了",
    "开始拍",
    "开始拍照",
    "拍一下",
)

# 明确要拍照，作业模式下也直接拍（跳过引导）
IMMEDIATE_PHOTO_PHRASES = (
    "拍照",
    "拍一张",
    "拍个照",
    "拍张照片",
    "照相",
    "拍个照片",
)


def _extract_plain_text(text: str) -> str:
    if not text:
        return ""
    s = (text or "").strip()
    if s.startswith("{") and "content" in s:
        try:
            data = json.loads(s)
            if isinstance(data, dict) and data.get("content"):
                return str(data["content"]).strip()
        except (json.JSONDecodeError, TypeError):
            pass
    return s


def normalize_utterance(text: str) -> str:
    plain = _extract_plain_text(text)
    _, filtered = remove_punctuation_and_length(plain)
    return (filtered or plain).strip()


def match_enter(text: str) -> bool:
    norm = normalize_utterance(text)
    if not norm:
        return False
    return any(p in norm for p in ENTER_PHRASES)


def match_exit(text: str) -> bool:
    norm = normalize_utterance(text)
    if not norm:
        return False
    return any(p in norm for p in EXIT_PHRASES)


def match_photo_ready(text: str) -> bool:
    norm = normalize_utterance(text)
    if not norm:
        return False
    return any(p in norm for p in PHOTO_READY_PHRASES)


def looks_like_immediate_photo(text: str) -> bool:
    norm = normalize_utterance(text)
    if not norm:
        return False
    return any(p in norm for p in IMMEDIATE_PHOTO_PHRASES)


def clear_photo_flow(conn) -> None:
    conn.homework_photo_pending = False
    conn.homework_photo_capture_now = False


def is_photo_pending(conn) -> bool:
    return bool(getattr(conn, "homework_photo_pending", False))


def is_active(conn) -> bool:
    return getattr(conn, "active_mode", None) == MODE_ID


def get_idle_timeout_sec(conn) -> int:
    cfg = getattr(conn, "config", None) or {}
    block = cfg.get("homework_tutor_mode") or {}
    if isinstance(block, dict):
        try:
            return max(60, int(block.get("idle_timeout_sec", DEFAULT_IDLE_TIMEOUT_SEC)))
        except (TypeError, ValueError):
            pass
    return DEFAULT_IDLE_TIMEOUT_SEC


def maybe_expire_mode(conn) -> bool:
    """超时则清除模式，返回是否刚过期。"""
    if not is_active(conn):
        return False
    entered = getattr(conn, "homework_mode_entered_at", None)
    if not entered:
        return False
    if time.time() - float(entered) <= get_idle_timeout_sec(conn):
        return False
    try:
        from core.learning.learning_api_client import on_homework_exit

        on_homework_exit(conn, "idle_timeout")
    except Exception:
        pass
    conn.active_mode = None
    conn.homework_mode_entered_at = None
    conn.homework_mode_just_expired = True
    return True


def enter_mode(conn) -> None:
    conn.active_mode = MODE_ID
    conn.homework_mode_entered_at = time.time()
    conn.homework_mode_just_expired = False
    clear_photo_flow(conn)
    try:
        from core.learning.learning_api_client import on_homework_enter

        on_homework_enter(conn)
    except Exception:
        pass


def exit_mode(conn) -> None:
    try:
        from core.learning.learning_api_client import on_homework_exit

        on_homework_exit(conn, "manual")
    except Exception:
        pass
    conn.active_mode = None
    conn.homework_mode_entered_at = None
    conn.homework_mode_just_expired = False
    clear_photo_flow(conn)


def touch_activity(conn) -> None:
    if is_active(conn):
        conn.homework_mode_entered_at = time.time()


def attach_to_environment_context(conn, ctx: dict) -> None:
    """写入 environment_context 供 zhiban-agent 使用。"""
    maybe_expire_mode(conn)
    if not is_active(conn):
        return
    ctx["active_mode"] = MODE_ID
    ht = {
        "active": True,
        "entered_at": getattr(conn, "homework_mode_entered_at", None),
        "idle_timeout_sec": get_idle_timeout_sec(conn),
        "photo_pending": is_photo_pending(conn),
        "photo_capture_now": bool(getattr(conn, "homework_photo_capture_now", False)),
    }
    ctx["homework_tutor"] = ht
    # 一次性标记：本轮回合触发拍照后清除
    if getattr(conn, "homework_photo_capture_now", False):
        conn.homework_photo_capture_now = False


def try_resolve_mode_phrase(conn, text: str) -> Tuple[bool, Optional[str]]:
    """
    识别进入/退出唤醒词。
    返回 (handled, reply_text)。handled=True 时调用方应播报 reply 并跳过 LLM。
    """
    if getattr(conn, "homework_mode_just_expired", False):
        conn.homework_mode_just_expired = False
        if not match_enter(text) and not match_exit(text):
            return True, TIMEOUT_REPLY

    if maybe_expire_mode(conn):
        if not match_enter(text) and not match_exit(text):
            return True, TIMEOUT_REPLY

    if match_exit(text):
        if is_active(conn):
            exit_mode(conn)
            return True, EXIT_REPLY
        return True, NOT_IN_MODE_REPLY

    if match_enter(text):
        if is_active(conn):
            return True, ALREADY_IN_MODE_REPLY
        enter_mode(conn)
        return True, ENTER_REPLY

    if is_active(conn):
        touch_activity(conn)

    return False, None


def apply_zhiban_homework_meta(conn, meta: dict) -> None:
    """应用 zhiban 下发的作业辅导会话控制（如 photo_guide 进入摆拍等待）。"""
    if not isinstance(meta, dict):
        return
    action = (meta.get("homework_action") or "").strip()
    if action == "photo_guide" and is_active(conn):
        conn.homework_photo_pending = True


def try_resolve_homework_photo_phrase(conn, text: str) -> Tuple[bool, Optional[str]]:
    """
    作业辅导模式下仅处理「好了」→ photo_capture_now。
    摆拍引导由 zhiban router 识别 knowledge_qa 后下发。
    """
    if not is_active(conn):
        return False, None

    if is_photo_pending(conn) and match_photo_ready(text):
        conn.homework_photo_pending = False
        conn.homework_photo_capture_now = True

    return False, None
