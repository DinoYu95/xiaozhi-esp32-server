# -*- coding: utf-8 -*-
"""
家长端聊天 HTTP 接口：供 manager-api 调用，执行 ASR（可选）+ zhiban-agent 对话。
支持非流式 /internal/parent/chat 与流式 SSE /internal/parent/chat/stream。
"""
import asyncio
import json
import time
from aiohttp import web

from config.logger import setup_logging
from core.zhibanAgent import ZhibanAgentClient

TAG = __name__
logger = setup_logging()


def _normalize_openai_messages(raw) -> list | None:
    """将请求体中的 messages 规范为 [{role, content}]，仅保留 user/assistant。"""
    if not isinstance(raw, list):
        return None
    out = []
    for m in raw:
        if not isinstance(m, dict):
            continue
        role = m.get("role")
        content = m.get("content")
        if role not in ("user", "assistant"):
            continue
        if content is None:
            continue
        s = str(content).strip()
        if not s:
            continue
        out.append({"role": role, "content": s})
    return out or None


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

        # 服务端权威状态：仅 active 影子任务；空列表 = 当前无进行中任务（非「记忆」或历史对话）
        shadow_missions = environment_context.get("shadow_missions")
        shadow_suffix = ""
        if isinstance(shadow_missions, list):
            if len(shadow_missions) == 0:
                shadow_suffix = (
                    "【影子任务状态】当前**没有**进行中的家长影子任务（与库 active 一致）。"
                    "多轮历史里若曾讨论过学习任务/影子任务，视为已结束或旧话题；除非家长明确要新布置，否则勿再追问是否还要做之前的任务，勿默认孩子仍有待办。"
                    "zhiban 长期记忆若与上述冲突，以本段为准。"
                )
            else:
                shadow_suffix = (
                    f"【影子任务状态】当前有 {len(shadow_missions)} 条进行中的家长影子任务，"
                    "条目与 id以 environment_context.shadow_missions 为准；仅可围绕这些任务引导。"
                    "不要把对话历史里已结束或过期的任务当成仍有效。"
                )

        # 孩子近期对话改由 zhiban-agent 按需拉取（见 environment_context 中的 agent_id、mac_address）
        if not parts and not shadow_suffix:
            self.logger.bind(tag=TAG).debug("家长聊天无孩子信息可注入，environment_context=%s", environment_context)
            return text

        prefix = ""
        if parts:
            prefix = "【助手已知信息】" + "；".join(parts)
        if shadow_suffix:
            prefix = (prefix + "。" if prefix else "") + shadow_suffix
        prefix += "。若家长询问孩子近期聊天内容，请调用 manager-api 的 GET /config/parent/child-chat-history（参数 agent_id、mac_address 已传入 environment_context）拉取真实对话后回答，严禁编造。"
        prefix += (
            "若家长要设置**长期规则**（如不要讲鬼故事），请调用 add_parent_rule；"
            "若要安排**接下来一段时间引导孩子做事**（限时影子任务），请调用 upsert_shadow_mission（title、instructions、duration_minutes）；"
            "取消影子任务请调用 cancel_shadow_mission。"
            "若家长要看孩子现在在做什么、远程看画面，**必须**调用 fetch_child_device_snapshot；"
            "device_id 已在 environment_context，不要编造「设备未绑定」。"
            "若任务描述模糊（如「提醒完成学习任务」），先与家长多轮追问：具体科目或作业项、校内作业还是家庭任务、截止时间或完成标准，再 upsert；信息不足时不要调用 upsert_shadow_mission。"
            "parent_user_id、child_id、mac_address 已在 environment_context 中，勿编造。\n\n家长问："
        )
        self.logger.bind(tag=TAG).info("家长聊天注入孩子信息: %s", prefix[:80])
        return prefix + text

    def _prepare_text_and_messages(
        self,
        text: str,
        environment_context: dict | None,
        messages_raw,
    ) -> tuple[str, list | None]:
        """
        多轮时：仅对最后一条 user 注入【助手已知信息】前缀，历史轮次保持原文（与库中一致）。
        单轮或 messages 无效时：整段 text 注入。
        """
        env = environment_context if isinstance(environment_context, dict) else {}
        msgs = _normalize_openai_messages(messages_raw)
        if msgs and len(msgs) >= 2 and msgs[-1].get("role") == "user":
            msgs = [dict(x) for x in msgs]
            msgs[-1]["content"] = self._inject_child_context(msgs[-1]["content"], env)
            return msgs[-1]["content"], msgs
        return self._inject_child_context(text, env), None

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
        if isinstance(environment_context, dict):
            env = dict(environment_context)
            root_device_id = (body.get("device_id") or body.get("deviceId") or "").strip()
            if root_device_id and not env.get("device_id"):
                env["device_id"] = root_device_id
            environment_context = env

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
        text_to_send, zhiban_messages = self._prepare_text_and_messages(
            text, environment_context, body.get("messages")
        )
        self.logger.bind(tag=TAG).info(
            "家长聊天发往 zhiban-agent: 文本前200字=%s, 附带messages条数=%s",
            (text_to_send or "")[:200],
            len(zhiban_messages) if zhiban_messages else 0,
        )

        client = ZhibanAgentClient(self._zhiban_config)
        t_zhiban = time.perf_counter()
        self.logger.bind(tag=TAG).info(
            "家长聊天调用 zhiban-agent 开始 session_id={} text_len={}",
            session_id,
            len(text_to_send or ""),
        )
        # 必须在 executor 中调用：client.chat 为同步 httpx，会阻塞 aiohttp 事件循环；
        # 看娃 fast-path 会回调本机 /internal/parent/device-snapshot，同步阻塞会导致 30s 假超时。
        reply, meta = await asyncio.to_thread(
            client.chat,
            text=text_to_send,
            session_id=session_id,
            user_id=user_id or None,
            speaker_context=speaker_context if isinstance(speaker_context, dict) else None,
            skill_ids=skill_ids if isinstance(skill_ids, list) else None,
            environment_context=environment_context if isinstance(environment_context, dict) else None,
            messages=zhiban_messages,
        )
        self.logger.bind(tag=TAG).info(
            "家长聊天调用 zhiban-agent 结束 session_id={} elapsed_ms={:.1f} reply_len={} has_meta={}",
            session_id,
            (time.perf_counter() - t_zhiban) * 1000.0,
            len(reply or ""),
            bool(meta),
        )
        result = {"reply": reply or ""}
        if meta:
            result["meta"] = meta
            ps = meta.get("parent_snapshot") if isinstance(meta, dict) else None
            if isinstance(ps, dict) and ps.get("requestId"):
                self.logger.bind(tag=TAG).info(
                    "家长聊天看娃 meta requestId={} code={}",
                    ps.get("requestId"),
                    ps.get("code"),
                )
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
        if isinstance(environment_context, dict):
            env = dict(environment_context)
            root_device_id = (body.get("device_id") or body.get("deviceId") or "").strip()
            if root_device_id and not env.get("device_id"):
                env["device_id"] = root_device_id
            environment_context = env

        if not text:
            return web.json_response({"detail": "text 不能为空"}, status=400)
        if not session_id:
            return web.json_response({"detail": "session_id 不能为空"}, status=400)

        text_to_send, zhiban_messages = self._prepare_text_and_messages(
            text, environment_context, body.get("messages")
        )

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
                for frame in client.stream(
                    text=text_to_send,
                    session_id=session_id,
                    user_id=user_id or None,
                    speaker_context=speaker_context,
                    skill_ids=skill_ids,
                    environment_context=environment_context,
                    messages=zhiban_messages,
                ):
                    asyncio.run_coroutine_threadsafe(queue.put(frame), loop)
                asyncio.run_coroutine_threadsafe(queue.put(None), loop)
            except Exception as e:
                self.logger.bind(tag=TAG).exception("流式调用异常: %s", e)
                asyncio.run_coroutine_threadsafe(queue.put(None), loop)

        loop.run_in_executor(None, run_stream)

        try:
            while True:
                item = await asyncio.wait_for(queue.get(), timeout=60.0)
                if item is None:
                    break
                if getattr(item, "kind", None) == "meta":
                    continue
                chunk = item.payload if hasattr(item, "payload") else item
                if not chunk or not isinstance(chunk, str):
                    continue
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
