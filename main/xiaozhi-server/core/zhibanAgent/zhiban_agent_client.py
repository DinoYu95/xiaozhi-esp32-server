# -*- coding: utf-8 -*-
"""
智伴 Agent 客户端：向 zhiban-agent 发起 /api/chat（非流）或 /api/chat/stream（流式），用于儿童对话、知识问答、故事、游戏等。
设备不直连 zhiban-agent，由 xiaozhi-server 在需要时调用本客户端，再将回复经 TTS 返回设备。
"""
from typing import Optional, Iterator, Dict, Any, List

import httpx

from config.logger import setup_logging

TAG = __name__
logger = setup_logging()

DEFAULT_TIMEOUT = 30.0
# HTTP 连接复用：keep-alive 减少首包延迟
HTTPX_LIMITS = httpx.Limits(max_keepalive_connections=4, max_connections=10)


def _log_zhiban_payload_diagnostics(payload: dict, mode: str) -> None:
    """排查是否带上成长陪伴 / 家长规则：看 text 前缀与 environment_context 键。"""
    text = payload.get("text") or ""
    env = payload.get("environment_context") or {}
    cg = (env.get("companion_growth_prompt") or "").strip()
    rules = env.get("parent_rules") or []
    n_rules = len([r for r in rules if r and str(r).strip()]) if isinstance(rules, list) else 0
    has_growth_block = "【成长陪伴与对话风格】" in text
    has_rules_block = "【家长为本设备设置的规则" in text
    logger.bind(tag=TAG).info(
        "zhiban-agent {}: text总长度={}, 含成长陪伴前缀={}, companion_env长度={}, 家长规则条数={}, 含规则前缀={}, speaker_name={}",
        mode,
        len(text),
        has_growth_block,
        len(cg),
        n_rules,
        has_rules_block,
        (payload.get("speaker_context") or {}).get("speaker_name"),
    )
    if cg:
        logger.bind(tag=TAG).debug(
            "zhiban-agent environment_context.companion_growth_prompt 前120字: {}",
            cg[:120],
        )


class ZhibanAgentClient:
    """调用 zhiban-agent 的 /api/chat 或 /api/chat/stream 接口。"""

    def __init__(self, config):
        """
        :param config: 配置字典，支持 base_url、timeout。
                      通常来自 self.config.get("zhiban_agent", {}) 或智控台下发的配置。
        """
        self.config = config or {}
        # 与 OpenAI 等 LLM 一致：智控台「接口地址」可能存为 url，base_url 优先
        self.base_url = (
            self.config.get("base_url") or self.config.get("url") or ""
        ).rstrip("/")
        self.timeout = float(self.config.get("timeout", DEFAULT_TIMEOUT))
        self._client: Optional[httpx.Client] = None

    def _get_client(self) -> Optional[httpx.Client]:
        """获取复用的 httpx 客户端，无 base_url 时返回 None。"""
        if not self.base_url:
            return None
        if self._client is None:
            self._client = httpx.Client(
                timeout=self.timeout,
                limits=HTTPX_LIMITS,
            )
        return self._client

    def chat(
        self,
        text: str,
        session_id: str,
        user_id: Optional[str] = None,
        speaker_context: Optional[Dict[str, Any]] = None,
        skill_ids: Optional[list] = None,
        environment_context: Optional[Dict[str, Any]] = None,
        messages: Optional[List[Dict[str, str]]] = None,
    ) -> Optional[str]:
        """
        非流式：发送用户文本到 zhiban-agent /api/chat，返回完整助手回复。
        一说话人多技能：传 skill_ids 列表，由 zhiban 按意图选 skill。
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
        if speaker_context:
            payload["speaker_context"] = speaker_context
        if skill_ids:
            payload["skill_ids"] = skill_ids
        if environment_context:
            payload["environment_context"] = environment_context
        if messages:
            payload["messages"] = messages

        _log_zhiban_payload_diagnostics(payload, "非流式")

        try:
            client = self._get_client()
            if not client:
                return None
            r = client.post(
                "%s/api/chat" % self.base_url,
                json=payload,
            )
            r.raise_for_status()
            data = r.json()
            reply = data.get("reply") if isinstance(data, dict) else None
            if reply is not None:
                return reply if isinstance(reply, str) else str(reply)
            logger.bind(tag=TAG).warning("zhiban_agent 返回无 reply 字段: {}", data)
            return None
        except httpx.HTTPError as e:
            logger.bind(tag=TAG).error("zhiban_agent 请求失败: {}", e)
            return None
        except Exception as e:
            logger.bind(tag=TAG).exception("zhiban_agent 调用异常: {}", e)
            return None

    def stream(
        self,
        text: str,
        session_id: str,
        user_id: Optional[str] = None,
        speaker_context: Optional[Dict[str, Any]] = None,
        skill_ids: Optional[list] = None,
        environment_context: Optional[Dict[str, Any]] = None,
        messages: Optional[List[Dict[str, str]]] = None,
    ) -> Iterator[str]:
        """
        流式：POST /api/chat/stream，按 SSE 解析，逐块 yield 文本。
        一说话人多技能：传 skill_ids 列表，由 zhiban 按意图选 skill。
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
        if speaker_context:
            payload["speaker_context"] = speaker_context
        if skill_ids:
            payload["skill_ids"] = skill_ids
        if environment_context:
            payload["environment_context"] = environment_context
        if messages:
            payload["messages"] = messages

        _log_zhiban_payload_diagnostics(payload, "流式")

        try:
            client = self._get_client()
            if not client:
                return
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
            logger.bind(tag=TAG).error("zhiban_agent 流式请求失败: {}", e)
        except Exception as e:
            logger.bind(tag=TAG).exception("zhiban_agent 流式调用异常: {}", e)
