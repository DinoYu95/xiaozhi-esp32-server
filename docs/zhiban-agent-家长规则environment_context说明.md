# zhiban-agent：家长规则（parent_rules）通过 environment_context 传递

## 背景

家长为设备设置的规则（如「不要讲鬼故事」「少提零食」）由 manager-api 的 `getAgentModels` 返回，xiaozhi-server 会将其追加到 prompt。但 **ZhibanAgent 不接收 xiaozhi 的 system prompt**（只传 user/assistant 消息），因此规则需通过 `environment_context` 单独传递。

## xiaozhi-server 已做

- `connection.py` 在 `_build_environment_context()` 中增加 `parent_rules` 字段
- 格式：`environment_context["parent_rules"]` = `["不要讲鬼故事", "少提零食", ...]`

## zhiban-agent 需要做

在生成回复前，从 `environment_context` 读取 `parent_rules`，注入到 LLM 的 system prompt 或 context 中，例如：

```python
parent_rules = (environment_context or {}).get("parent_rules") or []
if parent_rules:
    rules_text = "\n".join(f"- {r}" for r in parent_rules if r and str(r).strip())
    if rules_text:
        system_prompt += "\n\n家长为本设备设置的规则（请严格遵守）：\n" + rules_text
```

确保 child/device 对话时，LLM 能收到并遵守这些规则。
