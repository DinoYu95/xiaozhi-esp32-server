import os
import time
import queue
import aiohttp
import asyncio
import requests
import traceback
from config.logger import setup_logging
from core.utils.tts import MarkdownCleaner
from core.utils.util import check_model_key
from core.providers.tts.base import TTSProviderBase
from core.utils import opus_encoder_utils, textUtils
from core.providers.tts.dto.dto import SentenceType, ContentType, InterfaceType

TAG = __name__
logger = setup_logging()

BOSON_PCM_SAMPLE_RATE = 24000


class TTSProvider(TTSProviderBase):
    def __init__(self, config, delete_audio_file):
        super().__init__(config, delete_audio_file)
        self.interface_type = InterfaceType.SINGLE_STREAM
        self.api_key = config.get("api_key") or os.environ.get("BOSON_API_KEY")
        self.api_url = config.get(
            "api_url", "https://api.boson.ai/v1/audio/speech"
        )
        self.model = config.get("model", "higgs-audio-v3-tts")
        if config.get("private_voice"):
            self.voice = config.get("private_voice")
        else:
            self.voice = config.get("voice", "default")
        timeout = config.get("request_timeout", 30)
        self.request_timeout = float(timeout) if timeout else 30.0
        self.output_file = config.get("output_dir", "tmp/")
        self.audio_format = "pcm"
        self.before_stop_play_files = []

        model_key_msg = check_model_key("TTS", self.api_key)
        if model_key_msg:
            logger.bind(tag=TAG).error(model_key_msg)

        # Boson 流式接口固定返回 24kHz PCM
        self.opus_encoder = opus_encoder_utils.OpusEncoderUtils(
            sample_rate=BOSON_PCM_SAMPLE_RATE, channels=1, frame_size_ms=60
        )
        self.pcm_buffer = bytearray()

    def _build_headers(self):
        return {
            "Authorization": f"Bearer {self.api_key}",
            "Content-Type": "application/json",
        }

    def _build_payload(self, text):
        return {
            "model": self.model,
            "input": text,
            "voice": self.voice,
            "response_format": "pcm",
            "stream": True,
        }

    def tts_text_priority_thread(self):
        """流式文本处理线程"""
        while not self.conn.stop_event.is_set():
            try:
                message = self.tts_text_queue.get(timeout=1)
                if message.sentence_type == SentenceType.FIRST:
                    self.tts_stop_request = False
                    self.processed_chars = 0
                    self.tts_text_buff = []
                    self.before_stop_play_files.clear()
                elif ContentType.TEXT == message.content_type:
                    self.tts_text_buff.append(message.content_detail)
                    segment_text = self._get_segment_text()
                    if segment_text:
                        self.to_tts_single_stream(segment_text)

                elif ContentType.FILE == message.content_type:
                    logger.bind(tag=TAG).info(
                        f"添加音频文件到待播放列表: {message.content_file}"
                    )
                    if message.content_file and os.path.exists(message.content_file):
                        self._process_audio_file_stream(
                            message.content_file,
                            callback=lambda audio_data: self.handle_audio_file(
                                audio_data, message.content_detail
                            ),
                        )

                if message.sentence_type == SentenceType.LAST:
                    self._process_remaining_text_stream(True)

            except queue.Empty:
                continue
            except Exception as e:
                logger.bind(tag=TAG).error(
                    f"处理TTS文本失败: {str(e)}, 类型: {type(e).__name__}, 堆栈: {traceback.format_exc()}"
                )

    def _process_remaining_text_stream(self, is_last=False):
        full_text = "".join(self.tts_text_buff)
        remaining_text = full_text[self.processed_chars :]
        if remaining_text:
            segment_text = textUtils.get_string_no_punctuation_or_emoji(
                remaining_text
            )
            if segment_text:
                self.to_tts_single_stream(segment_text, is_last)
                self.processed_chars += len(full_text)
            else:
                self._process_before_stop_play_files()
        else:
            self._process_before_stop_play_files()

    def to_tts_single_stream(self, text, is_last=False):
        text = MarkdownCleaner.clean_markdown(text)
        if not text or not text.strip():
            if is_last:
                self._process_before_stop_play_files()
            return None

        max_repeat_time = 5
        for attempt in range(max_repeat_time):
            try:
                asyncio.run(self.text_to_speak(text, is_last))
                if attempt > 0:
                    logger.bind(tag=TAG).info(
                        f"语音生成成功: {text}，重试{attempt}次"
                    )
                return None
            except Exception as e:
                logger.bind(tag=TAG).warning(
                    f"语音生成失败{attempt + 1}次: {text}，错误: {e}"
                )

        logger.bind(tag=TAG).error(
            f"语音生成失败: {text}，请检查网络或 Boson API 是否正常"
        )
        return None

    async def text_to_speak(self, text, is_last):
        """调用 Boson 流式 TTS，接收 PCM 并编码为 Opus"""
        payload = self._build_payload(text)
        headers = self._build_headers()
        timeout = aiohttp.ClientTimeout(total=self.request_timeout)

        frame_bytes = int(
            self.opus_encoder.sample_rate
            * self.opus_encoder.channels
            * self.opus_encoder.frame_size_ms
            / 1000
            * 2
        )

        async with aiohttp.ClientSession(timeout=timeout) as session:
            async with session.post(
                self.api_url, json=payload, headers=headers
            ) as resp:
                if resp.status != 200:
                    body = await resp.text()
                    logger.bind(tag=TAG).error(
                        f"Boson TTS请求失败: {resp.status}, {body}"
                    )
                    self.tts_audio_queue.put((SentenceType.LAST, [], None))
                    raise RuntimeError(f"Boson TTS HTTP {resp.status}: {body}")

                self.pcm_buffer.clear()
                self.tts_audio_queue.put((SentenceType.FIRST, [], text))

                async for chunk in resp.content.iter_any():
                    data = chunk[0] if isinstance(chunk, (list, tuple)) else chunk
                    if not data:
                        continue

                    self.pcm_buffer.extend(data)

                    while len(self.pcm_buffer) >= frame_bytes:
                        frame = bytes(self.pcm_buffer[:frame_bytes])
                        del self.pcm_buffer[:frame_bytes]
                        self.opus_encoder.encode_pcm_to_opus_stream(
                            frame,
                            end_of_stream=False,
                            callback=self.handle_opus,
                        )

                if self.pcm_buffer:
                    self.opus_encoder.encode_pcm_to_opus_stream(
                        bytes(self.pcm_buffer),
                        end_of_stream=True,
                        callback=self.handle_opus,
                    )
                    self.pcm_buffer.clear()

                if is_last:
                    self._process_before_stop_play_files()

    def audio_to_pcm_data_stream(self, audio_file_path, callback=None):
        from core.utils.util import audio_to_data_stream

        return audio_to_data_stream(
            audio_file_path,
            is_opus=False,
            callback=callback,
            sample_rate=BOSON_PCM_SAMPLE_RATE,
            opus_encoder=None,
        )

    def audio_to_opus_data_stream(self, audio_file_path, callback=None):
        from core.utils.util import audio_to_data_stream

        return audio_to_data_stream(
            audio_file_path,
            is_opus=True,
            callback=callback,
            sample_rate=BOSON_PCM_SAMPLE_RATE,
            opus_encoder=self.opus_encoder,
        )

    async def close(self):
        await super().close()
        if hasattr(self, "opus_encoder"):
            self.opus_encoder.close()

    def to_tts(self, text: str) -> list:
        """非流式合成，用于测试场景"""
        start_time = time.time()
        text = MarkdownCleaner.clean_markdown(text)
        if not text or not text.strip():
            return []

        payload = {
            "model": self.model,
            "input": text,
            "voice": self.voice,
            "response_format": "pcm",
            "stream": True,
        }

        try:
            with requests.post(
                self.api_url,
                json=payload,
                headers=self._build_headers(),
                stream=True,
                timeout=self.request_timeout,
            ) as response:
                if response.status_code != 200:
                    logger.bind(tag=TAG).error(
                        f"Boson TTS请求失败: {response.status_code}, {response.text}"
                    )
                    return []

                logger.bind(tag=TAG).info(
                    f"Boson TTS请求成功: {text}, 耗时: {time.time() - start_time:.3f}s"
                )

                opus_datas = []
                pcm_data = bytearray()
                for chunk in response.iter_content(chunk_size=4096):
                    if chunk:
                        pcm_data.extend(chunk)

                frame_bytes = int(
                    self.opus_encoder.sample_rate
                    * self.opus_encoder.channels
                    * self.opus_encoder.frame_size_ms
                    / 1000
                    * 2
                )

                for i in range(0, len(pcm_data), frame_bytes):
                    frame = bytes(pcm_data[i : i + frame_bytes])
                    if len(frame) < frame_bytes:
                        frame = frame + b"\x00" * (frame_bytes - len(frame))
                    self.opus_encoder.encode_pcm_to_opus_stream(
                        frame,
                        end_of_stream=(i + frame_bytes >= len(pcm_data)),
                        callback=lambda opus: opus_datas.append(opus),
                    )

                return opus_datas

        except Exception as e:
            logger.bind(tag=TAG).error(f"Boson TTS请求异常: {e}")
            return []
