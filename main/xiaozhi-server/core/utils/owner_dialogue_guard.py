# -*- coding: utf-8 -*-
"""主孩子独占对话窗口：打断与上下文注入策略。"""

from __future__ import annotations

from typing import Any, Optional


def is_owner_speaker(conn: Any, speaker_id: Optional[str] = None) -> bool:
    owner_vp_id = getattr(conn, "owner_child_voice_print_id", None)
    sid = speaker_id if speaker_id is not None else getattr(conn, "current_speaker_id", None)
    return bool(owner_vp_id and sid and sid == owner_vp_id)


def is_owner_dialogue_busy(conn: Any) -> bool:
    """主孩子独占窗口：LLM 在途或 TTS 播读中。"""
    if not getattr(conn, "owner_exclusive_active", False):
        return False
    inflight = int(getattr(conn, "_chat_inflight", 0) or 0) > 0
    speaking = bool(getattr(conn, "client_is_speaking", False))
    return inflight or speaking


def should_accept_speech(conn: Any, speaker_id: Optional[str] = None) -> bool:
    """
    是否受理本段语音。
    独占窗口内仅主孩子声纹可进入；空闲时任意说话人可开新轮。
    """
    if not is_owner_dialogue_busy(conn):
        return True
    return is_owner_speaker(conn, speaker_id)


def maybe_clear_owner_exclusive(conn: Any) -> None:
    """LLM 与 TTS 均结束后释放主孩子独占。"""
    inflight = int(getattr(conn, "_chat_inflight", 0) or 0)
    speaking = bool(getattr(conn, "client_is_speaking", False))
    if inflight <= 0 and not speaking:
        conn.owner_exclusive_active = False


def should_attach_owner_child_context(conn: Any) -> bool:
    """是否注入主孩子 child_id / 影子任务 / 成长档案。"""
    st = (getattr(conn, "current_round_speaker_type", None) or "unknown").strip().lower()
    return st == "owner_child" and is_owner_speaker(conn)


def is_cautious_unknown_turn(conn: Any) -> bool:
    """空闲时声纹未识别：谨慎模式，不套用主孩子档案。"""
    st = (getattr(conn, "current_round_speaker_type", None) or "unknown").strip().lower()
    return st == "unknown" and not is_owner_speaker(conn)
