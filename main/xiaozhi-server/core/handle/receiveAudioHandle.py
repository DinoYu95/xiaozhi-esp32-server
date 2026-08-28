import time
import json
import uuid
import asyncio
from core.utils.util import audio_to_data
from core.providers.tts.dto.dto import ContentType, TTSMessageDTO
from core.handle.abortHandle import handleAbortMessage
from core.handle.intentHandler import handle_user_intent
from core.utils.output_counter import check_device_output_limit
from core.handle.sendAudioHandle import (
    send_stt_message,
    SentenceType,
    AUDIO_FRAME_DURATION,
)
from core.utils.owner_dialogue_guard import (
    allow_vad_barge_in,
    barge_in_permissive,
    is_owner_dialogue_busy,
    is_owner_speaker,
    is_machine_busy,
    should_accept_speech,
)

TAG = __name__

DEFAULT_DEVICE_BIND_PROMPT = "请打开智伴未来微信小程序扫码绑定设备"
DEFAULT_CONSENT_BLOCKED_PROMPT = (
    "请先由主账号家长在小程序中阅读并同意儿童隐私保护说明。"
    "同意后设备才能继续使用，本次对话即将结束。"
)


def speak_one_sentence(conn, text: str):
    """单句 TTS：需 FIRST/LAST 收尾，否则无标点文本不会触发合成"""
    conn.client_abort = False
    if not conn.sentence_id:
        conn.sentence_id = str(uuid.uuid4().hex)
    conn.tts.tts_text_queue.put(
        TTSMessageDTO(
            sentence_id=conn.sentence_id,
            sentence_type=SentenceType.FIRST,
            content_type=ContentType.ACTION,
        )
    )
    conn.tts.tts_one_sentence(conn, ContentType.TEXT, content_detail=text)
    conn.tts.tts_text_queue.put(
        TTSMessageDTO(
            sentence_id=conn.sentence_id,
            sentence_type=SentenceType.LAST,
            content_type=ContentType.ACTION,
        )
    )


def get_device_bind_prompt(conn) -> str:
    """未绑定设备 TTS 文案：优先读智控台参数 device_bind_prompt.prompt"""
    bind_prompt_cfg = conn.config.get("device_bind_prompt") or {}
    if isinstance(bind_prompt_cfg, dict):
        prompt = bind_prompt_cfg.get("prompt")
        if prompt is not None and str(prompt).strip():
            return str(prompt).strip()
    return DEFAULT_DEVICE_BIND_PROMPT


def get_consent_blocked_prompt(conn) -> str:
    """主账号未同意协议 TTS 文案"""
    if getattr(conn, "consent_prompt", None) and str(conn.consent_prompt).strip():
        return str(conn.consent_prompt).strip()
    consent_cfg = conn.config.get("consent_blocked") or {}
    if isinstance(consent_cfg, dict):
        prompt = consent_cfg.get("prompt")
        if prompt is not None and str(prompt).strip():
            return str(prompt).strip()
    return DEFAULT_CONSENT_BLOCKED_PROMPT


def _set_current_round_speaker_type(conn):
    """根据 current_speaker_id 与 owner_child_voice_print_id 设置 current_round_speaker_type。"""
    owner_vp_id = getattr(conn, "owner_child_voice_print_id", None)
    speaker_id = getattr(conn, "current_speaker_id", None)
    if owner_vp_id and speaker_id == owner_vp_id:
        conn.current_round_speaker_type = "owner_child"
    elif speaker_id:
        conn.current_round_speaker_type = "other_child"  # 已知声纹非主孩子，简化为 other_child
    else:
        conn.current_round_speaker_type = "unknown"


async def handleAudioMessage(conn, audio):
    # 当前片段是否有人说话
    have_voice = conn.vad.is_vad(conn, audio)
    # 如果设备刚刚被唤醒，短暂忽略VAD检测
    if hasattr(conn, "just_woken_up") and conn.just_woken_up:
        have_voice = False
        # 设置一个短暂延迟后恢复VAD检测
        if not hasattr(conn, "vad_resume_task") or conn.vad_resume_task.done():
            conn.vad_resume_task = asyncio.create_task(resume_vad_detection(conn))
        return
    # 非 busy 状态清零去抖累计
    chat_inflight = int(getattr(conn, "_chat_inflight", 0) or 0) > 0
    machine_busy = is_machine_busy(conn)
    if not machine_busy:
        conn._barge_in_voice_accum_ms = 0
    # 播读/LLM 在途：宽松模式下 VAD 累计人声后打断；严格模式等 ASR+声纹
    if have_voice and machine_busy and conn.client_listen_mode != "manual":
        if allow_vad_barge_in(conn):
            min_ms = int(conn.config.get("barge_in_min_voice_ms", 400))
            if min_ms <= 0:
                conn.logger.bind(tag=TAG).info(
                    "播读/在途中人声触发打断（VAD 即时） speaking=%s inflight=%s permissive=%s",
                    conn.client_is_speaking,
                    chat_inflight,
                    barge_in_permissive(conn),
                )
                await handleAbortMessage(conn)
            else:
                conn._barge_in_voice_accum_ms += AUDIO_FRAME_DURATION
                if conn._barge_in_voice_accum_ms >= min_ms:
                    conn.logger.bind(tag=TAG).info(
                        "播读/在途中人声累计约 %dms（阈值 %dms），VAD 打断 permissive=%s",
                        conn._barge_in_voice_accum_ms,
                        min_ms,
                        barge_in_permissive(conn),
                    )
                    conn._barge_in_voice_accum_ms = 0
                    await handleAbortMessage(conn)
    elif machine_busy and not have_voice:
        conn._barge_in_voice_accum_ms = 0
    # 设备长时间空闲检测，用于say goodbye
    await no_voice_close_connect(conn, have_voice)
    # 接收音频
    await conn.asr.receive_audio(conn, audio, have_voice)


async def resume_vad_detection(conn):
    # 等待2秒后恢复VAD检测
    await asyncio.sleep(2)
    conn.just_woken_up = False


async def startToChat(conn, text):
    # 检查输入是否是JSON格式（包含说话人信息）
    speaker_name = None
    language_tag = None
    actual_text = text

    try:
        # 尝试解析JSON格式的输入
        if text.strip().startswith("{") and text.strip().endswith("}"):
            data = json.loads(text)
            if "speaker" in data and "content" in data:
                speaker_name = data["speaker"]
                language_tag = data.get("language")
                actual_text = data["content"]
                conn.logger.bind(tag=TAG).info(f"解析到说话人信息: {speaker_name}")
                if "speaker_id" in data:
                    conn.current_speaker_id = data["speaker_id"]
                # 直接使用JSON格式的文本，不解析
                actual_text = text
    except (json.JSONDecodeError, KeyError):
        # 如果解析失败，继续使用原始文本
        pass

    # 保存说话人信息到连接对象
    if speaker_name:
        conn.current_speaker = speaker_name
    else:
        conn.current_speaker = None
    # 多角色：当前轮说话人类型，供打断策略与 skill 路由
    _set_current_round_speaker_type(conn)
    speaker_id = getattr(conn, "current_speaker_id", None)
    if not should_accept_speech(conn, speaker_id):
        conn.logger.bind(tag=TAG).info(
            "机器忙且非已录声纹说话人，丢弃 speech speaker_id=%s type=%s",
            speaker_id,
            getattr(conn, "current_round_speaker_type", None),
        )
        return
    if is_owner_speaker(conn, speaker_id):
        conn.owner_exclusive_active = True
    elif not is_owner_dialogue_busy(conn):
        conn.owner_exclusive_active = False
    # 保存语种信息到连接对象
    if language_tag:
        conn.current_language_tag = language_tag
    else:
        conn.current_language_tag = "zh"

    if conn.need_bind:
        await check_bind_device(conn)
        return

    if getattr(conn, "need_consent", False):
        await check_consent_device(conn)
        return

    # 如果当日的输出字数大于限定的字数
    if conn.max_output_size > 0:
        if check_device_output_limit(
            conn.headers.get("device-id"), conn.max_output_size
        ):
            await max_out_size(conn)
            return
    # manual 模式下不打断正在播放的内容；LLM 在途（尚未播读）时也应打断上一轮
    if conn.client_listen_mode != "manual":
        chat_inflight = int(getattr(conn, "_chat_inflight", 0) or 0) > 0
        if conn.client_is_speaking or chat_inflight:
            conn.logger.bind(tag=TAG).info(
                "startToChat 前打断上一轮 speaking=%s inflight=%s",
                conn.client_is_speaking,
                chat_inflight,
            )
            await handleAbortMessage(conn)

    # 首先进行意图分析，使用实际文本内容
    intent_handled = await handle_user_intent(conn, actual_text)

    if intent_handled:
        # 如果意图已被处理，不再进行聊天
        return

    from core.zhibanAgent.zhiban_connection_hooks import is_zhiban_connection
    from core.zhibanAgent.homework_tutor_mode import (
        PHOTO_CAPTURE_START_REPLY,
        try_resolve_homework_photo_phrase,
        try_resolve_mode_phrase,
    )

    will_photo_capture = False
    if is_zhiban_connection(conn):
        handled, mode_reply = try_resolve_mode_phrase(conn, actual_text)
        if handled and mode_reply:
            await send_stt_message(conn, actual_text)
            speak_one_sentence(conn, mode_reply)
            return
        try_resolve_homework_photo_phrase(conn, actual_text)
        will_photo_capture = bool(getattr(conn, "homework_photo_capture_now", False))
        from core.zhibanAgent.homework_tutor_mode import is_active as homework_active

        if homework_active(conn) and not handled:
            try:
                from core.learning.learning_api_client import on_user_turn

                on_user_turn(conn, actual_text)
            except Exception:
                pass

    # 成长星图 / 心绪图谱：主孩子发言仅缓存 transcript，会话结束再 batch 分析
    try:
        from core.growth_portrait.growth_portrait_api_client import append_user_turn
        from core.mind_portrait.mind_portrait_api_client import append_user_turn as append_mind_user_turn

        append_user_turn(conn, actual_text)
        append_mind_user_turn(conn, actual_text)
    except Exception:
        pass

    # 意图未被处理，继续常规聊天流程，使用实际文本内容
    await send_stt_message(conn, actual_text)
    if is_zhiban_connection(conn) and will_photo_capture:
        speak_one_sentence(conn, PHOTO_CAPTURE_START_REPLY)
        conn._learning_pending_photo_user_text = actual_text
    conn.executor.submit(conn.chat, actual_text)


async def no_voice_close_connect(conn, have_voice):
    if have_voice:
        # 若已触发结束对话（再见），用户再次说话时取消并中止 goodbye chat
        if getattr(conn, "close_after_chat", False):
            conn.logger.bind(tag=TAG).info("检测到用户再次说话，取消结束对话并中止再见")
            conn.close_after_chat = False
            conn.client_abort = True
        conn.last_activity_time = time.time() * 1000
        return
    # 只有在已经初始化过时间戳的情况下才进行超时检查
    if conn.last_activity_time > 0.0:
        no_voice_time = time.time() * 1000 - conn.last_activity_time
        close_connection_no_voice_time = int(
            conn.config.get("close_connection_no_voice_time", 120)
        )
        if (
            not conn.close_after_chat
            and no_voice_time > 1000 * close_connection_no_voice_time
        ):
            conn.close_after_chat = True
            conn.client_abort = False
            end_prompt = conn.config.get("end_prompt", {})
            if end_prompt and end_prompt.get("enable", True) is False:
                conn.logger.bind(tag=TAG).info("结束对话，无需发送结束提示语")
                await conn.close()
                return
            prompt = end_prompt.get("prompt")
            if not prompt:
                prompt = "请你以```时间过得真快```未来头，用富有感情、依依不舍的话来结束这场对话吧。！"
            await startToChat(conn, prompt)


async def max_out_size(conn):
    # 播放超出最大输出字数的提示
    conn.client_abort = False
    text = "不好意思，我现在有点事情要忙，明天这个时候我们再聊，约好了哦！明天不见不散，拜拜！"
    await send_stt_message(conn, text)
    file_path = "config/assets/max_output_size.wav"
    opus_packets = await audio_to_data(file_path)
    conn.tts.tts_audio_queue.put((SentenceType.LAST, opus_packets, text))
    conn.close_after_chat = True


async def check_bind_device(conn):
    if conn.tts is None:
        conn.logger.bind(tag=TAG).warning("绑定提示跳过: TTS 尚未初始化")
        return
    if conn.bind_code:
        # 确保bind_code是6位数字（仍用于设备屏显/二维码，语音不再逐位播报）
        if len(conn.bind_code) != 6:
            conn.logger.bind(tag=TAG).error(f"无效的绑定码格式: {conn.bind_code}")
            text = "绑定码格式错误，请检查配置。"
            await send_stt_message(conn, text)
            speak_one_sentence(conn, text)
            return

        text = get_device_bind_prompt(conn)
        conn.logger.bind(tag=TAG).info(f"播放未绑定设备提示: {text}")
        await send_stt_message(conn, text)
        speak_one_sentence(conn, text)
    else:
        text = "没有找到该设备的版本信息，请正确配置 OTA地址，然后重新编译固件。"
        conn.logger.bind(tag=TAG).info("播放未绑定设备提示（无绑定码）")
        await send_stt_message(conn, text)
        music_path = "config/assets/bind_not_found.wav"
        opus_packets = await audio_to_data(music_path)
        conn.tts.tts_audio_queue.put((SentenceType.LAST, opus_packets, text))


async def check_consent_device(conn, *, exit_session: bool = True):
    """主账号未同意隐私协议：播报提示；exit_session=True 时播完后结束会话。"""
    if hasattr(conn, "try_refresh_consent_from_api"):
        if await conn.try_refresh_consent_from_api():
            return
    if conn.tts is None:
        conn.logger.bind(tag=TAG).warning("协议提示跳过: TTS 尚未初始化")
        return
    if getattr(conn, "_consent_prompt_playing", False):
        return
    conn._consent_prompt_playing = True
    try:
        text = get_consent_blocked_prompt(conn)
        conn.logger.bind(tag=TAG).info(f"播放隐私协议未同意提示: {text}")
        conn.client_abort = True
        await send_stt_message(conn, text)
        speak_one_sentence(conn, text)
        if exit_session:
            conn.close_after_chat = True
            asyncio.create_task(_exit_session_after_consent_prompt(conn))
    finally:
        conn._consent_prompt_playing = False


async def _exit_session_after_consent_prompt(conn):
    """等待 TTS 播完后断开，避免儿童继续对话。"""
    await asyncio.sleep(8)
    if not getattr(conn, "need_consent", False):
        return
    conn.logger.bind(tag=TAG).info("主账号未同意隐私协议，结束设备会话")
    try:
        from core.handle.sendAudioHandle import send_tts_message

        await send_tts_message(conn, "stop", None)
    except Exception:
        pass
    conn.client_is_speaking = False
    if conn.websocket and not conn.stop_event.is_set():
        await conn.close(conn.websocket)
