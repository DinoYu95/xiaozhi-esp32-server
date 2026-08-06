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


def _config_flag(conn: Any, key: str, default: bool) -> bool:
    v = conn.config.get(key, default)
    if isinstance(v, str):
        return v.strip().lower() not in ("0", "false", "no", "off")
    return bool(v)


def _owner_child_barge_exclusive_enabled(conn: Any) -> bool:
    """
    为 true 时恢复「主孩子独占 + 声纹门禁」打断（见 _should_accept_speech_strict 等）。
    为 false（默认）：机器忙时任意说话人均可插话打断。
    """
    return _config_flag(conn, "owner_child_barge_in_exclusive", False)


def _voiceprint_gate_enabled(conn: Any) -> bool:
    """是否要求「已录入声纹」才允许在机器忙时插话/受理语音（仅 strict 模式使用）。"""
    return _config_flag(conn, "barge_in_requires_voiceprint", True)


def is_registered_voiceprint_speaker(conn: Any, speaker_id: Optional[str] = None) -> bool:
    """说话人 id 是否命中本设备已配置的声纹库。"""
    sid = speaker_id if speaker_id is not None else getattr(conn, "current_speaker_id", None)
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
    宽松模式（owner_child_barge_in_exclusive=false）：允许 VAD 打断。
    严格模式：须 barge_in_requires_voiceprint=false 才 VAD 打断，否则等 ASR+声纹。
    """
    if not _owner_child_barge_exclusive_enabled(conn):
        return True
    if _voiceprint_gate_enabled(conn):
        return False
    return True


def barge_in_permissive(conn: Any) -> bool:
    """是否处于「任意说话人均可插话」模式（与 owner_child_barge_in_exclusive 相反）。"""
    return not _owner_child_barge_exclusive_enabled(conn)


def _should_accept_speech_strict(conn: Any, speaker_id: Optional[str] = None) -> bool:
    """原主孩子/声纹门禁逻辑，owner_child_barge_in_exclusive=true 时使用。"""
    if not is_machine_busy(conn):
        return True
    if not _voiceprint_gate_enabled(conn):
        if not is_owner_dialogue_busy(conn):
            return True
        return is_owner_speaker(conn, speaker_id)
    return is_registered_voiceprint_speaker(conn, speaker_id)


def should_accept_speech(conn: Any, speaker_id: Optional[str] = None) -> bool:
    """
    是否受理本段语音。
    宽松模式：机器忙时也受理任意说话人。
    严格模式：机器忙时仅已识别且符合声纹/主孩子策略者可插话。
    """
    if not _owner_child_barge_exclusive_enabled(conn):
        return True
    return _should_accept_speech_strict(conn, speaker_id)


def _should_interrupt_on_voiceprint_strict(
    conn: Any, speaker_id: Optional[str] = None
) -> bool:
    """原 ASR+声纹完成后的打断判定。"""
    if not is_machine_busy(conn):
        return False
    if not _voiceprint_gate_enabled(conn):
        return is_owner_dialogue_busy(conn) and is_owner_speaker(conn, speaker_id)
    return is_registered_voiceprint_speaker(conn, speaker_id)


def should_interrupt_on_voiceprint(conn: Any, speaker_id: Optional[str] = None) -> bool:
    """ASR+声纹完成后，是否应打断当前播读/在途对话。"""
    if not is_machine_busy(conn):
        return False
    if not _owner_child_barge_exclusive_enabled(conn):
        return True
    return _should_interrupt_on_voiceprint_strict(conn, speaker_id)


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
