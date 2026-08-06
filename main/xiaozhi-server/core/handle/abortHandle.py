import json

TAG = __name__


async def handleAbortMessage(conn):
    conn.logger.bind(tag=TAG).info(
        "Abort message received speaking=%s inflight=%s",
        getattr(conn, "client_is_speaking", False),
        int(getattr(conn, "_chat_inflight", 0) or 0),
    )
    conn.client_abort = True
    if getattr(conn, "tts", None) and hasattr(conn.tts, "abort_playback"):
        try:
            conn.tts.abort_playback()
        except Exception as e:
            conn.logger.bind(tag=TAG).warning("TTS abort_playback 失败: %s", e)
    asr = getattr(conn, "asr", None)
    if asr is not None:
        stop_ws = getattr(asr, "stop_ws_connection", None)
        if callable(stop_ws):
            try:
                stop_ws()
            except Exception as e:
                conn.logger.bind(tag=TAG).warning("ASR stop_ws_connection 失败: %s", e)
    conn.clear_queues()
    conn.reset_audio_states()
    conn.clearSpeakStatus()
    await conn.websocket.send(
        json.dumps({"type": "tts", "state": "stop", "session_id": conn.session_id})
    )
    conn.logger.bind(tag=TAG).info("Abort message received-end")
