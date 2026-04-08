# -*- coding: utf-8 -*-
"""
家长端聊天 WebSocket 处理器：小程序直连 xiaozhi-server 时使用。
与设备 WebSocket（8000 端口）完全独立，部署在 HTTP 服务（8003 端口）。
"""
import asyncio
import json
from urllib.parse import parse_qs

from aiohttp import web
from config.logger import setup_logging
from config.manage_api_client import (
    validate_parent_token,
    save_parent_chat,
    fetch_parent_zhiban_memory_context,
)
from core.zhibanAgent import ZhibanAgentClient

TAG = __name__
logger = setup_logging()


async def handle_parent_chat_ws(request: web.Request) -> web.WebSocketResponse:
    """
    家长端聊天 WebSocket：GET /parent/chat/ws?token=xxx
    鉴权通过后，接收 JSON 消息 {childId, content, audioId?}，流式推送助手回复。
    """
    ws = web.WebSocketResponse()
    await ws.prepare(request)

    # 从 query 获取 token
    parsed = parse_qs(request.rel_url.query_string)
    tokens = parsed.get("token", [])
    token = tokens[0] if tokens else ""

    parent_user_id = None
    try:
        parent_user_id = await validate_parent_token(token)
    except Exception as e:
        logger.bind(tag=TAG).warning("家长 token 校验异常: %s", e)
    if not parent_user_id:
        await ws.send_str(json.dumps({"type": "error", "message": "鉴权失败"}))
        await ws.close()
        return ws

    logger.bind(tag=TAG).info("家长端 WebSocket 连接建立: parentUserId=%s", parent_user_id)
    config = request.app.get("config", {})
    zhiban_config = config.get("zhiban_agent", {})
    client = ZhibanAgentClient(zhiban_config)

    try:
        async for msg in ws:
            if msg.type == web.WSMsgType.TEXT:
                try:
                    body = json.loads(msg.data)
                    child_id = body.get("childId")
                    content = (body.get("content") or "").strip()
                    audio_id = body.get("audioId") or None
                    if not child_id or not content:
                        await ws.send_str(
                            json.dumps({"type": "error", "message": "childId 和 content 必填"})
                        )
                        continue
                    child_id = int(child_id)

                    session_id = f"parent_{parent_user_id}_{child_id}"
                    mem_ctx = await fetch_parent_zhiban_memory_context(
                        parent_user_id, child_id
                    )
                    if mem_ctx and mem_ctx.get("zhibanUserId"):
                        user_id = mem_ctx["zhibanUserId"]
                        child_nm = (mem_ctx.get("childName") or "").strip()
                        speaker_context = {
                            "speaker_type": "parent",
                            "speaker_name": f"家长（了解孩子：{child_nm}）"
                            if child_nm
                            else "家长",
                            "is_owner_child": False,
                        }
                        environment_context = {
                            "agent_id": mem_ctx.get("agentId") or "",
                            "mac_address": (mem_ctx.get("macAddress") or "").strip(),
                            "device_child_profile_for_parent": (
                                mem_ctx.get("deviceChildProfile") or ""
                            ).strip(),
                        }
                        logger.bind(tag=TAG).info(
                            "家长端 zhiban user_id=%s agentId 已带 environment",
                            user_id,
                        )
                    else:
                        user_id = f"parent_{parent_user_id}"
                        speaker_context = {
                            "speaker_type": "parent",
                            "speaker_name": "家长",
                            "is_owner_child": False,
                        }
                        environment_context = {}
                        logger.bind(tag=TAG).warning(
                            "未拉到 zhiban-memory-context，仍用 parent_ user_id，长期记忆可能对不齐"
                        )
                    loop = asyncio.get_event_loop()
                    queue = asyncio.Queue()

                    def _run_stream():
                        try:
                            for chunk in client.stream(
                                text=content,
                                session_id=session_id,
                                user_id=user_id,
                                speaker_context=speaker_context,
                                environment_context=environment_context
                                if environment_context
                                else None,
                                persist_memory=False,
                            ):
                                asyncio.run_coroutine_threadsafe(queue.put(chunk), loop)
                            asyncio.run_coroutine_threadsafe(queue.put(None), loop)
                        except Exception as e:
                            logger.bind(tag=TAG).exception("流式调用异常: %s", e)
                            asyncio.run_coroutine_threadsafe(queue.put(None), loop)

                    loop.run_in_executor(None, _run_stream)
                    full_reply = []
                    while True:
                        chunk = await asyncio.wait_for(queue.get(), timeout=90.0)
                        if chunk is None:
                            break
                        full_reply.append(chunk)
                        await ws.send_str(
                            json.dumps({"type": "chunk", "chunk": chunk}, ensure_ascii=False)
                        )
                    reply_text = "".join(full_reply) if full_reply else "抱歉，小助手暂时无法回复。"
                    await ws.send_str(json.dumps({"type": "done"}))
                    await save_parent_chat(
                        parent_user_id,
                        child_id,
                        content,
                        reply_text,
                        audio_id,
                    )
                except json.JSONDecodeError as e:
                    await ws.send_str(json.dumps({"type": "error", "message": "无效 JSON"}))
                except Exception as e:
                    logger.bind(tag=TAG).exception("处理消息失败: %s", e)
                    await ws.send_str(
                        json.dumps({"type": "error", "message": str(e)}, ensure_ascii=False)
                    )
            elif msg.type == web.WSMsgType.CLOSE:
                break
    finally:
        await ws.close()
        logger.bind(tag=TAG).info("家长端 WebSocket 连接关闭: parentUserId=%s", parent_user_id)
    return ws
