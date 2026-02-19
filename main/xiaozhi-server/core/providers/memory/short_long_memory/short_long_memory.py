# -*- coding: utf-8 -*-
"""
短期 + 长期记忆：短期为本地 LLM 摘要（同 mem_local_short），长期为 Mem0（阿里云自建或官方云）。
适用于 Mem0 已在阿里云部署好的场景，同时保留近期对话的结构化摘要。
"""

import json
import os
import time
import traceback

import httpx
import yaml

from config.config_loader import get_project_dir
from core.utils.util import check_model_key

from ..base import MemoryProviderBase, logger

TAG = __name__
SELF_HOSTED_TIMEOUT = 30

# 短期记忆摘要 prompt（与 mem_local_short 一致，用于本地结构化摘要）
SHORT_TERM_MEMORY_PROMPT = """
# 时空记忆编织者

## 核心使命
构建可生长的动态记忆网络，在有限空间内保留关键信息的同时，智能维护信息演变轨迹
根据对话记录，总结user的重要信息，以便在未来的对话中提供更个性化的服务

## 记忆法则
### 1. 三维度记忆评估（每次更新必执行）
| 维度       | 评估标准                  | 权重分 |
|------------|---------------------------|--------|
| 时效性     | 信息新鲜度（按对话轮次） | 40%    |
| 情感强度   | 含💖标记/重复提及次数     | 35%    |
| 关联密度   | 与其他信息的连接数量      | 25%    |

### 2. 动态更新机制
**名字变更处理示例：**
原始记忆："曾用名": ["张三"], "现用名": "张三丰"
触发条件：当检测到「我叫X」「称呼我Y」等命名信号时
操作流程：
1. 将旧名移入"曾用名"列表
2. 记录命名时间轴："2024-02-15 14:32:启用张三丰"
3. 在记忆立方追加：「从张三到张三丰的身份蜕变」

### 3. 空间优化策略
- **信息压缩术**：用符号体系提升密度
  - ✅"张三丰[北/软工/🐱]"
  - ❌"北京软件工程师，养猫"
- **淘汰预警**：当总字数≥900时触发
  1. 删除权重分<60且3轮未提及的信息
  2. 合并相似条目（保留时间戳最近的）

## 记忆结构
输出格式必须为可解析的json字符串，不需要解释、注释和说明，保存记忆时仅从对话提取信息，不要混入示例内容
```json
{
  "时空档案": {
    "身份图谱": {
      "现用名": "",
      "特征标记": []
    },
    "记忆立方": [
      {
        "事件": "入职新公司",
        "时间戳": "2024-03-20",
        "情感值": 0.9,
        "关联项": ["下午茶"],
        "保鲜期": 30
      }
    ]
  },
  "关系网络": {
    "高频话题": {"职场": 12},
    "暗线联系": [""]
  },
  "待响应": {
    "紧急事项": ["需立即处理的任务"],
    "潜在关怀": ["可主动提供的帮助"]
  },
  "高光语录": [
    "最打动人心的瞬间，强烈的情感表达，user的原话"
  ]
}
```
"""


def _extract_json_data(json_code):
    start = json_code.find("```json")
    end = json_code.find("```", start + 1) if start != -1 else -1
    if start == -1 or end == -1:
        try:
            json.loads(json_code)
            return json_code
        except Exception:
            return ""
    return json_code[start + 7 : end]


class MemoryProvider(MemoryProviderBase):
    """短期（本地 LLM 摘要）+ 长期（Mem0）组合记忆。"""

    def __init__(self, config, summary_memory=None):
        super().__init__(config)
        self.short_memory = ""
        self.save_to_file = True
        self.memory_path = os.path.join(
            get_project_dir(), "data", ".memory_short_long.yaml"
        )
        # Mem0 长期：自建 base_url 或官方 api_key
        self.base_url = (config.get("base_url") or "").rstrip("/")
        self.api_key = config.get("api_key", "")
        self._self_hosted = bool(self.base_url)
        self._long_term_client = None
        self.use_long_term = False
        self._init_long_term()
        # 短期记忆从文件加载在 init_memory(role_id) 之后
        if summary_memory:
            self.short_memory = summary_memory
        logger.bind(tag=TAG).info(
            "短期+长期记忆: 短期=本地摘要, 长期=Mem0(%s)"
            % ("自建" if self._self_hosted else "云")
        )

    def _init_long_term(self):
        if self._self_hosted:
            self.use_long_term = True
            return
        if check_model_key("Mem0ai", self.api_key):
            return
        self.use_long_term = True
        try:
            from mem0 import MemoryClient
            self._long_term_client = MemoryClient(api_key=self.api_key)
        except Exception as e:
            logger.bind(tag=TAG).error("长期记忆 Mem0 初始化失败: %s" % e)
            logger.bind(tag=TAG).debug(traceback.format_exc())
            self.use_long_term = False

    def _long_add(self, messages, user_id):
        if self._self_hosted:
            payload = {"messages": messages, "user_id": user_id}
            with httpx.Client(timeout=SELF_HOSTED_TIMEOUT) as client:
                r = client.post("%s/memories" % self.base_url, json=payload)
                r.raise_for_status()
                return r.json()
        return self._long_term_client.add(messages, user_id=user_id)

    def _long_search(self, query, user_id):
        if self._self_hosted:
            payload = {"query": query, "user_id": user_id}
            with httpx.Client(timeout=SELF_HOSTED_TIMEOUT) as client:
                r = client.post("%s/search" % self.base_url, json=payload)
                r.raise_for_status()
                data = r.json()
            if isinstance(data, list):
                return {"results": data}
            if "results" in data:
                return data
            return {"results": [data] if isinstance(data, dict) else []}
        return self._long_term_client.search(
            query, filters={"user_id": user_id}
        )

    def init_memory(
        self, role_id, llm, summary_memory=None, save_to_file=True, **kwargs
    ):
        super().init_memory(role_id, llm, **kwargs)
        self.save_to_file = save_to_file
        self._load_short_memory(summary_memory)

    def _load_short_memory(self, summary_memory):
        if summary_memory or not self.save_to_file:
            self.short_memory = summary_memory or ""
            return
        all_memory = {}
        if os.path.exists(self.memory_path):
            try:
                with open(self.memory_path, "r", encoding="utf-8") as f:
                    all_memory = yaml.safe_load(f) or {}
            except Exception:
                pass
        if self.role_id in all_memory:
            self.short_memory = all_memory[self.role_id] or ""

    def _save_short_memory_to_file(self):
        all_memory = {}
        if os.path.exists(self.memory_path):
            try:
                with open(self.memory_path, "r", encoding="utf-8") as f:
                    all_memory = yaml.safe_load(f) or {}
            except Exception:
                pass
        all_memory[self.role_id] = self.short_memory
        os.makedirs(os.path.dirname(self.memory_path), exist_ok=True)
        with open(self.memory_path, "w", encoding="utf-8") as f:
            yaml.dump(all_memory, f, allow_unicode=True)

    async def save_memory(self, msgs, session_id=None):
        if len(msgs) < 2:
            return None
        role_id = getattr(self, "role_id", None)
        if not role_id:
            return None

        # 1) 长期：写入 Mem0
        if self.use_long_term:
            try:
                messages = [
                    {"role": m.role, "content": m.content}
                    for m in msgs
                    if m.role != "system"
                ]
                self._long_add(messages, role_id)
                logger.bind(tag=TAG).debug("长期记忆已写入 Mem0")
            except Exception as e:
                logger.bind(tag=TAG).error("长期记忆保存失败: %s" % e)

        # 2) 短期：本地 LLM 摘要
        if getattr(self, "llm", None) is None:
            logger.bind(tag=TAG).debug("未配置 LLM，跳过短期记忆摘要")
            return None
        api_key = getattr(self.llm, "api_key", None)
        if check_model_key("记忆总结专用LLM", api_key):
            pass  # 仅打日志，仍尝试摘要
        msg_str = ""
        for msg in msgs:
            if msg.role == "user":
                msg_str += "User: %s\n" % msg.content
            elif msg.role == "assistant":
                msg_str += "Assistant: %s\n" % msg.content
        if self.short_memory:
            msg_str += "历史记忆：\n%s\n" % self.short_memory
        msg_str += "当前时间：%s" % time.strftime("%Y-%m-%d %H:%M:%S", time.localtime())

        if self.save_to_file:
            try:
                result = self.llm.response_no_stream(
                    SHORT_TERM_MEMORY_PROMPT,
                    msg_str,
                    max_tokens=2000,
                    temperature=0.2,
                )
                json_str = _extract_json_data(result)
                if json_str:
                    json.loads(json_str)
                    self.short_memory = json_str
                    self._save_short_memory_to_file()
                    logger.bind(tag=TAG).debug("短期记忆已更新")
            except Exception as e:
                logger.bind(tag=TAG).error("短期记忆摘要失败: %s" % e)
        return self.short_memory

    async def query_memory(self, query: str) -> str:
        role_id = getattr(self, "role_id", None)
        if not role_id:
            return ""

        short_str = (self.short_memory or "").strip()
        long_str = ""

        if self.use_long_term:
            try:
                results = self._long_search(query, role_id)
                if results and results.get("results"):
                    memories = []
                    for entry in results["results"]:
                        ts = entry.get("updated_at", "")
                        mem = entry.get("memory", "")
                        if ts and mem:
                            try:
                                dt = ts.split(".")[0]
                                fmt_ts = dt.replace("T", " ")
                            except Exception:
                                fmt_ts = ts
                            memories.append((ts, "[%s] %s" % (fmt_ts, mem)))
                    memories.sort(key=lambda x: x[0], reverse=True)
                    long_str = "\n".join(m for _, m in memories)
            except Exception as e:
                logger.bind(tag=TAG).error("长期记忆查询失败: %s" % e)

        parts = []
        if short_str:
            parts.append("## 近期记忆（短期）\n%s" % short_str)
        else:
            parts.append("## 近期记忆（短期）\n（暂无）")
        if long_str:
            parts.append("## 长期记忆\n%s" % long_str)
        else:
            parts.append("## 长期记忆\n（暂无）")
        return "\n\n".join(parts)
