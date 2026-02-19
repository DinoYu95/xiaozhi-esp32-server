# -*- coding: utf-8 -*-
"""
智伴 Agent 客户端：向 zhiban-agent 发起 /api/chat（非流）或 /api/chat/stream（流式），用于儿童对话、知识问答、故事、游戏等。
设备不直连 zhiban-agent，由 xiaozhi-server 在需要时调用本客户端，再将回复经 TTS 返回设备。
"""
from typing import Optional, Iterator

import httpx

from config.logger import setup_logging

TAG = __name__
logger = setup_logging()

DEFAULT_TIMEOUT = 30.0


class ZhibanAgentClient:
    """调用 zhiban-agent 的 /api/chat 或 /api/chat/stream 接口。"""

    def __init__(self, config):
        """
        :param config: 配置字典，支持 base_url、timeout。
                      通常来自 self.config.get("zhiban_agent", {}) 或智控台下发的配置。
        """
        self.config = config or {}
        self.base_url = (self.config.get("base_url") or "").rstrip("/")
        self.timeout = float(self.config.get("timeout", DEFAULT_TIMEOUT))

    def chat(
        self,
        text: str,
        session_id: str,
        user_id: Optional[str] = None,
    ) -> Optional[str]:
        """
        非流式：发送用户文本到 zhiban-agent /api/chat，返回完整助手回复。
        """
        if not self.base_url:
            logger.bind(tag=TAG).warning("zhiban_agent base_url 未配置，跳过智伴调用")
            return None
        if not (text or "").strip():
            return None

        payload = {
            "text": text.strip(),
            "session_id": session_id,
        }
        if user_id:
            payload["user_id"] = user_id

        try:
            with httpx.Client(timeout=self.timeout) as client:
                r = client.post(
                    "%s/api/chat" % self.base_url,
                    json=payload,
                )
                r.raise_for_status()
                data = r.json()
            reply = data.get("reply") if isinstance(data, dict) else None
            if reply is not None:
                return reply if isinstance(reply, str) else str(reply)
            logger.bind(tag=TAG).warning("zhiban_agent 返回无 reply 字段: %s", data)
            return None
        except httpx.HTTPError as e:
            logger.bind(tag=TAG).error("zhiban_agent 请求失败: %s", e)
            return None
        except Exception as e:
            logger.bind(tag=TAG).exception("zhiban_agent 调用异常: %s", e)
            return None

    def stream(
        self,
        text: str,
        session_id: str,
        user_id: Optional[str] = None,
    ) -> Iterator[str]:
        """
        流式：POST /api/chat/stream，按 SSE 解析，逐块 yield 文本。
        """
        if not self.base_url:
            logger.bind(tag=TAG).warning("zhiban_agent base_url 未配置，跳过智伴调用")
            return
        if not (text or "").strip():
            return

        payload = {
            "text": text.strip(),
            "session_id": session_id,
        }
        if user_id:
            payload["user_id"] = user_id

        try:
            with httpx.Client(timeout=self.timeout) as client:
                with client.stream(
                    "POST",
                    "%s/api/chat/stream" % self.base_url,
                    json=payload,
                ) as r:
                    r.raise_for_status()
                    for line in r.iter_lines():
                        if line and line.startswith("data: "):
                            chunk = line[6:].strip()
                            if chunk:
                                yield chunk
        except httpx.HTTPError as e:
            logger.bind(tag=TAG).error("zhiban_agent 流式请求失败: %s", e)
        except Exception as e:
            logger.bind(tag=TAG).exception("zhiban_agent 流式调用异常: %s", e)
