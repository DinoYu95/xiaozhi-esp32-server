import time
import json
import asyncio
from core.utils.util import audio_to_data
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
    is_owner_dialogue_busy,
    is_owner_speaker,
    is_machine_busy,
    should_accept_speech,
)

TAG = __name__


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
    # 播读/LLM 在途：默认不在 VAD 层打断，等 ASR+声纹确认已录声纹后再插话
    if have_voice and machine_busy and conn.client_listen_mode != "manual":
        if allow_vad_barge_in(conn):
            min_ms = int(conn.config.get("barge_in_min_voice_ms", 400))
            if min_ms <= 0:
                conn.logger.bind(tag=TAG).info(
                    "播读/在途中人声触发打断（VAD 即时） speaking=%s inflight=%s",
                    conn.client_is_speaking,
                    chat_inflight,
                )
                await handleAbortMessage(conn)
            else:
                conn._barge_in_voice_accum_ms += AUDIO_FRAME_DURATION
                if conn._barge_in_voice_accum_ms >= min_ms:
                    conn.logger.bind(tag=TAG).info(
                        "播读/在途中人声累计约 %dms（阈值 %dms），VAD 即时打断",
                        conn._barge_in_voice_accum_ms,
                        min_ms,
                    )
                    conn._barge_in_voice_accum_ms = 0
                    await handleAbortMessage(conn)
        # else: 等待 ASR+声纹，未识别声纹的说话不会触发 abort
    elif machine_busy:
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
            await handleAbortMessage(conn)

    # 首先进行意图分析，使用实际文本内容
    intent_handled = await handle_user_intent(conn, actual_text)

    if intent_handled:
        # 如果意图已被处理，不再进行聊天
        return

    # 意图未被处理，继续常规聊天流程，使用实际文本内容
    await send_stt_message(conn, actual_text)
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
    if conn.bind_code:
        # 确保bind_code是6位数字
        if len(conn.bind_code) != 6:
            conn.logger.bind(tag=TAG).error(f"无效的绑定码格式: {conn.bind_code}")
            text = "绑定码格式错误，请检查配置。"
            await send_stt_message(conn, text)
            return

        text = f"请登录控制面板，输入{conn.bind_code}，绑定设备。"
        await send_stt_message(conn, text)

        # 播放提示音
        music_path = "config/assets/bind_code.wav"
        opus_packets = await audio_to_data(music_path)
        conn.tts.tts_audio_queue.put((SentenceType.FIRST, opus_packets, text))

        # 逐个播放数字
        for i in range(6):  # 确保只播放6位数字
            try:
                digit = conn.bind_code[i]
                num_path = f"config/assets/bind_code/{digit}.wav"
                num_packets = await audio_to_data(num_path)
                conn.tts.tts_audio_queue.put((SentenceType.MIDDLE, num_packets, None))
            except Exception as e:
                conn.logger.bind(tag=TAG).error(f"播放数字音频失败: {e}")
                continue
        conn.tts.tts_audio_queue.put((SentenceType.LAST, [], None))
    else:
        # 播放未绑定提示
        conn.client_abort = False
        text = f"没有找到该设备的版本信息，请正确配置 OTA地址，然后重新编译固件。"
        await send_stt_message(conn, text)
        music_path = "config/assets/bind_not_found.wav"
        opus_packets = await audio_to_data(music_path)
        conn.tts.tts_audio_queue.put((SentenceType.LAST, opus_packets, text))
