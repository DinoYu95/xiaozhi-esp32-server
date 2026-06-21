import json

TAG = __name__


async def handleAbortMessage(conn):
    conn.logger.bind(tag=TAG).info("Abort message received")
    # 设置成打断状态，会自动打断llm、tts任务
    conn.client_abort = True
    if getattr(conn, "tts", None) and hasattr(conn.tts, "abort_playback"):
        try:
            conn.tts.abort_playback()
        except Exception as e:
            conn.logger.bind(tag=TAG).warning("TTS abort_playback 失败: %s", e)
    conn.clear_queues()
    conn.reset_audio_states()
    # 打断客户端说话状态
    await conn.websocket.send(
        json.dumps({"type": "tts", "state": "stop", "session_id": conn.session_id})
    )
    conn.clearSpeakStatus()
    conn.logger.bind(tag=TAG).info("Abort message received-end")
