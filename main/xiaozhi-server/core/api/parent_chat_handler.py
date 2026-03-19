# -*- coding: utf-8 -*-
"""
家长端聊天 HTTP 接口：供 manager-api 调用，执行 ASR（可选）+ zhiban-agent 对话。
支持非流式 /internal/parent/chat 与流式 SSE /internal/parent/chat/stream。
"""
import asyncio
import json
from aiohttp import web

from config.logger import setup_logging
from core.zhibanAgent import ZhibanAgentClient

TAG = __name__
logger = setup_logging()


class ParentChatHandler:
    """家长聊天 HTTP 处理器"""

    def __init__(self, config: dict):
        self.config = config
        self.logger = logger
        # 内部 API 鉴权：使用 manager-api 的 secret，与 manage_api_client 一致
        self._secret = config.get("manager-api", {}).get("secret", "")
        self._zhiban_config = config.get("zhiban_agent", {})

    def _inject_child_context(self, text: str, environment_context: dict) -> str:
        """将孩子信息、家长昵称、孩子近期对话记录注入到用户文本前，确保 LLM 能看到（兜底，因 zhiban-agent 可能未解析 environment_context）"""
        if not isinstance(environment_context, dict):
            return text
        parts = []
        parent_nickname = environment_context.get("parent_nickname")
        if parent_nickname and str(parent_nickname).strip():
            parts.append(f"正在与你对话的家长名叫「{parent_nickname.strip()}」")
        child_name = environment_context.get("child_name")
        if child_name and str(child_name).strip():
            parts.append(f"该设备的主孩子名叫「{child_name.strip()}」")
        child_birthday = environment_context.get("child_birthday")
        if child_birthday:
            parts.append(f"生日{child_birthday}")
        child_hobbies = environment_context.get("child_hobbies")
        if child_hobbies and str(child_hobbies).strip():
            parts.append(f"爱好：{child_hobbies.strip()}")
        child_school = environment_context.get("child_school")
        if child_school and str(child_school).strip():
            parts.append(f"学校：{child_school.strip()}")

        # 孩子近期对话改由 zhiban-agent 按需拉取（见 environment_context 中的 agent_id、mac_address）
        if not parts:
            self.logger.bind(tag=TAG).debug("家长聊天无孩子信息可注入，environment_context=%s", environment_context)
            return text

        prefix = "【助手已知信息】" + "；".join(parts)
        prefix += "。若家长询问孩子近期聊天内容，请调用 manager-api 的 GET /config/parent/child-chat-history（参数 agent_id、mac_address 已传入 environment_context）拉取真实对话后回答，严禁编造。"
        prefix += "若家长表达要为设备设置规则（如「不要讲鬼故事」「少提零食」），请调用 add_parent_rule 工具，参数 parent_user_id、mac_address 已传入 environment_context。\n\n家长问："
        self.logger.bind(tag=TAG).info("家长聊天注入孩子信息: %s", prefix[:80])
        return prefix + text

    def _check_auth(self, request: web.Request) -> bool:
        """校验 Authorization: Bearer {secret}"""
        auth = request.headers.get("Authorization")
        if not auth or not auth.startswith("Bearer "):
            return False
        token = auth[7:].strip()
        return token == self._secret and self._secret

    async def handle_post(self, request: web.Request) -> web.Response:
        """POST /internal/parent/chat"""
        if not self._check_auth(request):
            return web.json_response({"detail": "Unauthorized"}, status=401)

        try:
            body = await request.json()
        except Exception as e:
            self.logger.bind(tag=TAG).error("解析请求体失败: %s", e)
            return web.json_response({"detail": "Invalid JSON"}, status=400)

        text = (body.get("text") or "").strip()
        session_id = body.get("session_id") or ""
        user_id = body.get("user_id") or ""
        speaker_context = body.get("speaker_context")
        skill_ids = body.get("skill_ids")
        environment_context = body.get("environment_context")

        # 诊断日志：确认收到的 parent_nickname 和 environment_context
        env = environment_context if isinstance(environment_context, dict) else {}
        parent_nickname_recv = env.get("parent_nickname")
        self.logger.bind(tag=TAG).info(
            "家长聊天收到: parent_nickname=%s, child_name=%s, env_keys=%s",
            parent_nickname_recv, env.get("child_name"), list(env.keys()) if env else [],
        )

        if not text:
            return web.json_response({"detail": "text 不能为空"}, status=400)
        if not session_id:
            return web.json_response({"detail": "session_id 不能为空"}, status=400)

        # 诊断：收到的家长/孩子信息（用于排查「我是谁」「孩子是谁」不生效）
        env = environment_context if isinstance(environment_context, dict) else {}
        self.logger.bind(tag=TAG).info(
            "家长聊天收到 environment_context: parent_nickname=%s, child_name=%s",
            env.get("parent_nickname"),
            env.get("child_name"),
        )

        # 兜底：若 zhiban-agent 未解析 environment_context，将孩子信息直接注入到文本前，
        # 确保 LLM 能看到，能正确回答「你知道我家孩子是谁吗」等
        # 诊断：收到的 environment_context 与注入后的文本
        self.logger.bind(tag=TAG).info(
            "家长聊天收到 environment_context keys=%s, parent_nickname=%s",
            list(environment_context.keys()) if isinstance(environment_context, dict) else None,
            environment_context.get("parent_nickname") if isinstance(environment_context, dict) else None,
        )
        text_to_send = self._inject_child_context(text, environment_context)
        self.logger.bind(tag=TAG).info("家长聊天发往 zhiban-agent 的文本前 200 字: %s", (text_to_send or "")[:200])

        client = ZhibanAgentClient(self._zhiban_config)
        reply = client.chat(
            text=text_to_send,
            session_id=session_id,
            user_id=user_id or None,
            speaker_context=speaker_context if isinstance(speaker_context, dict) else None,
            skill_ids=skill_ids if isinstance(skill_ids, list) else None,
            environment_context=environment_context if isinstance(environment_context, dict) else None,
        )
        result = {"reply": reply or ""}
        return web.json_response(result)

    async def handle_post_stream(self, request: web.Request) -> web.StreamResponse:
        """POST /internal/parent/chat/stream，返回 SSE 流式回复"""
        if not self._check_auth(request):
            return web.json_response({"detail": "Unauthorized"}, status=401)

        try:
            body = await request.json()
        except Exception as e:
            self.logger.bind(tag=TAG).error("解析请求体失败: %s", e)
            return web.json_response({"detail": "Invalid JSON"}, status=400)

        text = (body.get("text") or "").strip()
        session_id = body.get("session_id") or ""
        user_id = body.get("user_id") or ""
        speaker_context = body.get("speaker_context") if isinstance(body.get("speaker_context"), dict) else None
        skill_ids = body.get("skill_ids") if isinstance(body.get("skill_ids"), list) else None
        environment_context = body.get("environment_context") if isinstance(body.get("environment_context"), dict) else None

        if not text:
            return web.json_response({"detail": "text 不能为空"}, status=400)
        if not session_id:
            return web.json_response({"detail": "session_id 不能为空"}, status=400)

        text_to_send = self._inject_child_context(text, environment_context)

        response = web.StreamResponse(
            status=200,
            headers={
                "Content-Type": "text/event-stream",
                "Cache-Control": "no-cache",
                "Connection": "keep-alive",
            },
        )
        await response.prepare(request)

        client = ZhibanAgentClient(self._zhiban_config)
        loop = asyncio.get_event_loop()
        queue = asyncio.Queue()

        def run_stream():
            try:
                for chunk in client.stream(
                    text=text_to_send,
                    session_id=session_id,
                    user_id=user_id or None,
                    speaker_context=speaker_context,
                    skill_ids=skill_ids,
                    environment_context=environment_context,
                ):
                    asyncio.run_coroutine_threadsafe(queue.put(chunk), loop)
                asyncio.run_coroutine_threadsafe(queue.put(None), loop)
            except Exception as e:
                self.logger.bind(tag=TAG).exception("流式调用异常: %s", e)
                asyncio.run_coroutine_threadsafe(queue.put(None), loop)

        loop.run_in_executor(None, run_stream)

        try:
            while True:
                chunk = await asyncio.wait_for(queue.get(), timeout=60.0)
                if chunk is None:
                    break
                line = "data: " + chunk.replace("\r", "").replace("\n", " ") + "\n\n"
                await response.write(line.encode("utf-8"))
        except asyncio.TimeoutError:
            self.logger.bind(tag=TAG).warning("流式响应超时")
        finally:
            await response.write_eof()
        return response

    async def handle_options_stream(self, request: web.Request) -> web.Response:
        """CORS preflight for stream"""
        return web.Response(
            status=200,
            headers={
                "Access-Control-Allow-Origin": "*",
                "Access-Control-Allow-Methods": "POST, OPTIONS",
                "Access-Control-Allow-Headers": "Content-Type, Authorization",
            },
        )

    async def handle_options(self, request: web.Request) -> web.Response:
        """CORS preflight"""
        return web.Response(
            status=200,
            headers={
                "Access-Control-Allow-Origin": "*",
                "Access-Control-Allow-Methods": "POST, OPTIONS",
                "Access-Control-Allow-Headers": "Content-Type, Authorization",
            },
        )
