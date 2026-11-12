"""一轮用户输入的 [RT] 埋点：从结束说话到首包下行音频。"""
import time
import uuid

TAG = __name__


def begin_user_round(conn, source: str) -> str:
    """新轮次开始计时（覆盖上一轮未结束的标记）。"""
    rid = uuid.uuid4().hex[:8]
    conn._rt_round_id = rid
    conn._rt_round_t0 = time.monotonic()
    conn._rt_round_source = source
    conn._rt_first_audio_logged = False
    conn._rt_tts_first_opus_logged = False
    conn._rt_huoshan_sentence_logged = False
    tts = getattr(conn, "tts", None)
    if tts is not None:
        tts._rt_sentence_synth_logged = False
        tts._rt_tts_text_sent_at = None
    if hasattr(conn, "logger"):
        conn.logger.bind(tag=TAG).info(f"[RT][{rid}] 轮次开始 source={source}")
    return rid


def round_id(conn) -> str:
    return getattr(conn, "_rt_round_id", "-")


def round_elapsed(conn):
    t0 = getattr(conn, "_rt_round_t0", None)
    if t0 is None:
        return None
    return time.monotonic() - t0


def log_rt(conn, message: str, *, tag: str = TAG):
    rid = round_id(conn)
    if hasattr(conn, "logger"):
        conn.logger.bind(tag=tag).info(f"[RT][{rid}] {message}")
    else:
        from config.logger import setup_logging

        setup_logging().bind(tag=tag).info(f"[RT][{rid}] {message}")


def maybe_log_first_opus(conn):
    if getattr(conn, "_rt_tts_first_opus_logged", False):
        return
    elapsed = round_elapsed(conn)
    if elapsed is None:
        return
    conn._rt_tts_first_opus_logged = True
    log_rt(conn, f"TTS 首包音频入队: {elapsed:.3f}s（轮次累计）")


def maybe_log_e2e_first_downlink(conn, text: str = ""):
    if getattr(conn, "_rt_first_audio_logged", False):
        return
    elapsed = round_elapsed(conn)
    if elapsed is None:
        return
    conn._rt_first_audio_logged = True
    snippet = (text or "")[:40]
    log_rt(
        conn,
        f"E2E 首段下行(结束说话→可播): {elapsed:.3f}s text={snippet!r}",
    )
