# -*- coding: utf-8 -*-
"""主孩子独占对话窗口与声纹打断策略。"""

from __future__ import annotations

from typing import Any, Optional


def is_owner_speaker(conn: Any, speaker_id: Optional[str] = None) -> bool:
    owner_vp_id = getattr(conn, "owner_child_voice_print_id", None)
    sid = speaker_id if speaker_id is not None else getattr(conn, "current_speaker_id", None)
    return bool(owner_vp_id and sid and sid == owner_vp_id)


def is_machine_busy(conn: Any) -> bool:
    """LLM 在途或 TTS 播读中。"""
    inflight = int(getattr(conn, "_chat_inflight", 0) or 0) > 0
    speaking = bool(getattr(conn, "client_is_speaking", False))
    return inflight or speaking


def is_owner_dialogue_busy(conn: Any) -> bool:
    """主孩子独占窗口：LLM 在途或 TTS 播读中。"""
    if not getattr(conn, "owner_exclusive_active", False):
        return False
    return is_machine_busy(conn)


def _voiceprint_gate_enabled(conn: Any) -> bool:
    """是否要求「已录入声纹」才允许在机器忙时插话/受理语音。"""
    v = conn.config.get("barge_in_requires_voiceprint", True)
    if isinstance(v, str):
        return v.strip().lower() not in ("0", "false", "no", "off")
    return bool(v)


def is_registered_voiceprint_speaker(conn: Any, speaker_id: Optional[str] = None) -> bool:
    """说话人 id 是否命中本设备已配置的声纹库。"""
    sid = (speaker_id if speaker_id is not None else getattr(conn, "current_speaker_id", None))
    if not sid:
        return False
    vp = getattr(conn, "voiceprint_provider", None)
    if not vp or not getattr(vp, "enabled", False):
        return False
    ids = getattr(vp, "speaker_ids", None)
    if ids is None:
        ids = list(getattr(vp, "speaker_map", {}).keys())
    return sid in ids


def allow_vad_barge_in(conn: Any) -> bool:
    """
    VAD 层是否允许「听见人声就立刻打断」。
    产品默认关闭：VAD 无法区分是否已录声纹，插话须等 ASR+声纹识别后再决定。
    """
    if _voiceprint_gate_enabled(conn):
        return False
    return True


def should_accept_speech(conn: Any, speaker_id: Optional[str] = None) -> bool:
    """
    是否受理本段语音。
    机器忙时：仅已识别且已录入声纹的说话人可插话；未识别/未录声纹一律丢弃。
    空闲时：任意说话人均可开新轮。
    """
    if not is_machine_busy(conn):
        return True
    if not _voiceprint_gate_enabled(conn):
        if not is_owner_dialogue_busy(conn):
            return True
        return is_owner_speaker(conn, speaker_id)
    return is_registered_voiceprint_speaker(conn, speaker_id)


def should_interrupt_on_voiceprint(conn: Any, speaker_id: Optional[str] = None) -> bool:
    """ASR+声纹完成后，是否应打断当前播读/在途对话。"""
    if not is_machine_busy(conn):
        return False

    return True;
    # if not _voiceprint_gate_enabled(conn):
    #     return is_owner_dialogue_busy(conn) and is_owner_speaker(conn, speaker_id)
    # return is_registered_voiceprint_speaker(conn, speaker_id)


def maybe_clear_owner_exclusive(conn: Any) -> None:
    """LLM 与 TTS 均结束后释放主孩子独占。"""
    if not is_machine_busy(conn):
        conn.owner_exclusive_active = False


def should_attach_owner_child_context(conn: Any) -> bool:
    """是否注入主孩子 child_id / 影子任务 / 成长档案。"""
    st = (getattr(conn, "current_round_speaker_type", None) or "unknown").strip().lower()
    return st == "owner_child" and is_owner_speaker(conn)


def is_cautious_unknown_turn(conn: Any) -> bool:
    """空闲时声纹未识别：谨慎模式，不套用主孩子档案。"""
    st = (getattr(conn, "current_round_speaker_type", None) or "unknown").strip().lower()
    return st == "unknown" and not is_owner_speaker(conn)
