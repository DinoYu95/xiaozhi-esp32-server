# -*- coding: utf-8 -*-
"""
智伴 Agent 作为 LLM 提供方：将用户输入转发到 zhiban-agent 的 /api/chat，返回助手回复。
在「模型配置」里选本 LLM 时，对话会走智伴（儿童对话/知识问答/故事/游戏等），不再走其他大模型。
"""
import json

from config.logger import setup_logging
from core.providers.llm.base import LLMProviderBase
from core.zhibanAgent import ZhibanAgentClient

TAG = __name__
logger = setup_logging()

MAX_HISTORY_ROUNDS = 3


def _extract_user_text(raw):
    """从用户消息提取纯文本，支持 JSON 格式 {"content":"..."}"""
    if not isinstance(raw, str):
        return ""
    s = (raw or "").strip()
    if s.startswith("{") and "content" in s:
        try:
            data = json.loads(s)
            return (data.get("content") or "").strip() or s
        except Exception:
            return s
    return s


def _build_messages_from_dialogue(dialogue, max_rounds=MAX_HISTORY_ROUNDS):
    """从 dialogue 提取最近 max_rounds 轮对话，供 zhiban 理解多轮上下文"""
    if not isinstance(dialogue, list):
        return []
    turns = []
    for m in dialogue:
        role = m.get("role")
        if role not in ("user", "assistant"):
            continue
        content = m.get("content", "")
        if role == "user":
            content = _extract_user_text(content)
        if isinstance(content, str) and content.strip():
            turns.append({"role": role, "content": content.strip()})
    max_messages = max_rounds * 2
    return turns[-max_messages:] if len(turns) > max_messages else turns


class LLMProvider(LLMProviderBase):
    def __init__(self, config):
        """
        :param config: 配置字典，支持 base_url、timeout。
                      与 config.yaml 中 LLM.ZhibanAgent 或智控台下发的 LLM 配置一致。
        """
        self.config = config or {}
        self._client = ZhibanAgentClient(self.config)
        self._max_history_rounds = int(
            self.config.get("max_history_rounds", MAX_HISTORY_ROUNDS)
        )

    def response(self, session_id, dialogue, **kwargs):
        # 取最后一条用户消息（若为 JSON 则提取 content 字段作为纯文本）
        input_text = None
        if isinstance(dialogue, list):
            for message in reversed(dialogue):
                if message.get("role") == "user":
                    input_text = _extract_user_text(message.get("content", ""))
                    break
        if not (input_text or "").strip():
            logger.bind(tag=TAG).warning("ZhibanAgent: 无用户输入，跳过调用")
            return

        # 成长陪伴 + 家长规则：注入到文本前，确保 zhiban-agent 即使只读纯文本也能看到
        text_to_send = input_text.strip()
        env = kwargs.get("environment_context") or {}
        prefix_blocks = []
        cg = (env.get("companion_growth_prompt") or "").strip()
        if cg:
            prefix_blocks.append("【成长陪伴与对话风格】\n" + cg)
            logger.bind(tag=TAG).info(
                f"ZhibanAgent: 注入 companion_growth_prompt，长度={len(cg)}"
            )
        parent_rules = env.get("parent_rules") or []
        if not parent_rules:
            logger.bind(tag=TAG).debug(
                "ZhibanAgent: environment_context 无 parent_rules，keys={}",
                list(env.keys()) if env else None,
            )
        if parent_rules:
            rules_list = [r for r in parent_rules if r and str(r).strip()]
            if rules_list:
                prefix_blocks.append(
                    "【家长为本设备设置的规则，请严格遵守】\n"
                    + "\n".join(f"- {r}" for r in rules_list)
                )
                logger.bind(tag=TAG).info(
                    "ZhibanAgent: 注入家长规则 {} 条", len(rules_list)
                )
            else:
                logger.bind(tag=TAG).warning("ZhibanAgent: environment_context 有 parent_rules 但内容为空")
        else:
            logger.bind(tag=TAG).debug(
                "ZhibanAgent: environment_context 无 parent_rules (keys={})",
                list(env.keys()) if env else [],
            )
        sms = env.get("shadow_missions")
        if isinstance(sms, list) and len(sms) > 0:
            parts = [
                "【限时影子任务（可多条；priority 越小越优先；须遵守家长规则与安全底线；孩子抗拒则退让；"
                "当语境表明某条任务已达成时，可调用 complete_shadow_mission(mission_id)，mission_id 须为下列 id 之一）】"
            ]
            for sm in sms:
                if not isinstance(sm, dict):
                    continue
                if not (sm.get("title") or sm.get("instructions")):
                    continue
                mid = sm.get("id")
                pri = sm.get("priority", 0)
                parts.append(
                    f"- id={mid} priority={pri} 标题：{(sm.get('title') or '').strip()} "
                    f"说明：{(sm.get('instructions') or '').strip()} "
                    f"失效：{sm.get('endsAt')}"
                )
            if len(parts) > 1:
                prefix_blocks.append("\n".join(parts))
                logger.bind(tag=TAG).info("ZhibanAgent: 注入 shadow_missions count=%s", len(parts) - 1)
        else:
            sm = env.get("shadow_mission")
            if isinstance(sm, dict) and (sm.get("title") or sm.get("instructions")):
                sm_lines = [
                    "【限时影子任务（家长设置，失效前可优先引导；须遵守上文家长规则与安全底线；孩子明显抗拒时退让、不硬推）】",
                    f"标题：{(sm.get('title') or '').strip()}",
                    f"说明：{(sm.get('instructions') or '').strip()}",
                ]
                if sm.get("endsAt") is not None:
                    sm_lines.append(f"失效时间：{sm.get('endsAt')}")
                prefix_blocks.append("\n".join(sm_lines))
                logger.bind(tag=TAG).info("ZhibanAgent: 注入 shadow_mission id=%s", sm.get("id"))
        if prefix_blocks:
            text_to_send = "\n\n".join(prefix_blocks) + "\n\n用户说：" + text_to_send

        # 构建最近 N 轮对话，供 zhiban 理解上下文（谜语提示、故事续讲等）
        messages = _build_messages_from_dialogue(dialogue, self._max_history_rounds)

        # 优先流式：调用 /api/chat/stream，逐块 yield，便于 TTS 边收边播
        yielded_any = False
        for chunk in self._client.stream(
            text=text_to_send,
            session_id=session_id or "",
            user_id=kwargs.get("user_id"),
            speaker_context=kwargs.get("speaker_context"),
            skill_ids=kwargs.get("skill_ids"),
            environment_context=kwargs.get("environment_context"),
            messages=messages,
        ):
            yielded_any = True
            yield chunk
        # 若流式无任何输出（如服务未开 stream 或报错），回退为非流式
        if not yielded_any:
            reply = self._client.chat(
                text=text_to_send,
                session_id=session_id or "",
                user_id=kwargs.get("user_id"),
                speaker_context=kwargs.get("speaker_context"),
                skill_ids=kwargs.get("skill_ids"),
                environment_context=kwargs.get("environment_context"),
                messages=messages,
            )
            if reply:
                yield reply
            else:
                logger.bind(tag=TAG).warning("ZhibanAgent: 未获取到回复")

    def response_with_functions(self, session_id, dialogue, functions=None, **kwargs):
        """智伴不支持 function call，按普通对话转发到 zhiban-agent。"""
        for chunk in self.response(session_id, dialogue, **kwargs):
            yield chunk, None
