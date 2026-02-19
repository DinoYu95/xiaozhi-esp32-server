# -*- coding: utf-8 -*-
"""
智伴 Agent 作为 LLM 提供方：将用户输入转发到 zhiban-agent 的 /api/chat，返回助手回复。
在「模型配置」里选本 LLM 时，对话会走智伴（儿童对话/知识问答/故事/游戏等），不再走其他大模型。
"""
from config.logger import setup_logging
from core.providers.llm.base import LLMProviderBase
from core.zhibanAgent import ZhibanAgentClient

TAG = __name__
logger = setup_logging()


class LLMProvider(LLMProviderBase):
    def __init__(self, config):
        """
        :param config: 配置字典，支持 base_url、timeout。
                      与 config.yaml 中 LLM.ZhibanAgent 或智控台下发的 LLM 配置一致。
        """
        self.config = config or {}
        self._client = ZhibanAgentClient(self.config)

    def response(self, session_id, dialogue, **kwargs):
        # 取最后一条用户消息
        input_text = None
        if isinstance(dialogue, list):
            for message in reversed(dialogue):
                if message.get("role") == "user":
                    input_text = message.get("content", "")
                    break
        if not (input_text or "").strip():
            logger.bind(tag=TAG).warning("ZhibanAgent: 无用户输入，跳过调用")
            return

        # 优先流式：调用 /api/chat/stream，逐块 yield，便于 TTS 边收边播
        yielded_any = False
        for chunk in self._client.stream(
            text=input_text.strip(),
            session_id=session_id or "",
            user_id=kwargs.get("user_id"),
        ):
            yielded_any = True
            yield chunk
        # 若流式无任何输出（如服务未开 stream 或报错），回退为非流式
        if not yielded_any:
            reply = self._client.chat(
                text=input_text.strip(),
                session_id=session_id or "",
                user_id=kwargs.get("user_id"),
            )
            if reply:
                yield reply
            else:
                logger.bind(tag=TAG).warning("ZhibanAgent: 未获取到回复")

    def response_with_functions(self, session_id, dialogue, functions=None):
        """智伴不支持 function call，按普通对话转发到 zhiban-agent。"""
        for chunk in self.response(session_id, dialogue):
            yield chunk, None
