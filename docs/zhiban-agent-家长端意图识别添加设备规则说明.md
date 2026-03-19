# zhiban-agent：家长端意图识别添加设备规则

家长通过小程序与智伴对话时，若说「以后不要跟孩子讲鬼故事」「少提零食」等，智伴需识别**设置设备规则**的意图，并调用 manager-api 写入规则，供设备在与孩子对话时遵守。

本功能与「孩子聊天记录拉取」类似，采用 **zhiban-agent 意图识别 + 工具调用 manager-api** 的方式。

---

## 一、数据流

```
家长说「以后不要讲鬼故事」
    │
    ▼
zhiban-agent 识别意图 → 设置设备规则
    │
    ▼
调用 add_parent_rule 工具 → 请求 manager-api POST /config/parent/device-rule
    │
    ▼
manager-api 写入 parent_device_rule 表
    │
    ▼
设备下次拉取配置时收到 parent_rules → 智伴对话遵守规则
```

---

## 二、manager-api 提供的接口

| 项目 | 说明 |
|------|------|
| **路径** | `POST /config/parent/device-rule` |
| **鉴权** | `Authorization: Bearer {server_secret}`（与智控台参数 `server_secret` 一致） |
| **Content-Type** | `application/json` |
| **请求体** | `{ "parentUserId": 123, "macAddress": "B6:C8:35:D6:10:48", "ruleText": "不要讲鬼故事" }` |
| **成功响应** | `{ "code": 0, "data": { "id": 1, "ruleText": "不要讲鬼故事" } }` |
| **失败响应** | `{ "code": 非0, "msg": "错误信息" }` |

---

## 三、zhiban-agent 需要做的

### 3.1 入参来源

家长聊天请求中，`environment_context` 会包含：

- `parent_user_id`：家长用户 ID（Long），必填
- `mac_address`：设备 MAC 地址（与设备配置拉取一致）
- `agent_id`：智能体 ID（可选，用于校验）

zhiban-agent 在处理家长对话时，从 `environment_context` 读取 `parent_user_id` 和 `mac_address`。

### 3.2 识别时机

当检测到家长表达**要为设备设置规则**时（如「以后不要讲鬼故事」「少跟孩子提零食」「不要聊恐怖内容」等），触发 `add_parent_rule` 工具调用。

### 3.3 工具定义（add_parent_rule）

建议在家长 skill 中注册如下 function/tool：

```json
{
  "name": "add_parent_rule",
  "description": "家长为设备设置规则，如「不要讲鬼故事」「少提零食」。当家长在对话中表达要为孩子设备设置此类规则时调用。",
  "parameters": {
    "type": "object",
    "properties": {
      "rule_text": {
        "type": "string",
        "description": "规则内容，如「不要讲鬼故事」，需从家长话语中提炼，简洁明确，不超过 200 字"
      }
    },
    "required": ["rule_text"]
  }
}
```

**注意**：`parent_user_id` 和 `mac_address` 从当前会话的 `environment_context` 读取，**不需要**由 LLM 输出。

### 3.4 工具执行逻辑（伪代码）

```python
async def add_parent_rule(rule_text: str, environment_context: dict) -> dict:
    parent_user_id = environment_context.get("parent_user_id")
    mac_address = environment_context.get("mac_address")
    if not parent_user_id or not mac_address:
        return {"success": False, "message": "缺少 parent_user_id 或 mac_address"}
    url = f"{MANAGER_API_BASE}/config/parent/device-rule"
    body = {
        "parentUserId": parent_user_id,
        "macAddress": mac_address.strip(),
        "ruleText": rule_text.strip()[:200]
    }
    headers = {
        "Authorization": f"Bearer {MANAGER_API_SECRET}",
        "Content-Type": "application/json"
    }
    resp = await httpx.post(url, json=body, headers=headers)
    data = resp.json()
    if data.get("code") == 0:
        return {"success": True, "message": f"已为设备添加规则：{rule_text}"}
    return {"success": False, "message": data.get("msg", "添加失败")}
```

### 3.5 家长 skill instructions 补充

在家长专用 skill 的 instructions 中增加：

> 当家长表达要为设备设置规则时（如「以后不要讲鬼故事」「少提零食」「不要聊恐怖内容」），请调用 `add_parent_rule` 工具，将家长意图提炼为简洁的规则文本（如「不要讲鬼故事」）。调用成功后，用自然语言确认，如「好的，我已经记下了，以后和宝宝聊天时会避免讲鬼故事。」

---

## 四、配置

与「孩子聊天记录拉取」相同：

- `MANAGER_API_BASE`：manager-api 地址，如 `http://manager-api:8080`
- `MANAGER_API_SECRET`：智控台「参数字典」中的 `server_secret`

---

## 五、前置条件

1. 设备已由家长绑定（`parent_device_binding` 表有记录）。
2. 家长聊天时 `environment_context` 中已包含 `parent_user_id`、`mac_address`（由 manager-api 在调用 xiaozhi-server 时传入）。

---

## 六、与显式入口的关系

- **显式入口**：家长在小程序「规则」页手动添加/删除规则，调用 `POST /parent-api/device/rules` 等。
- **隐式入口**：家长在与智伴聊天时口头设置，由 zhiban-agent 识别意图并调用本接口。

两种方式写入同一张表 `parent_device_rule`，设备拉取配置时会收到合并后的 `parent_rules`。
