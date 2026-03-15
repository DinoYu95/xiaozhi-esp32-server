# zhiban-agent：家长端孩子聊天记录按需拉取

家长通过小程序与智伴对话时，若询问「宝宝最近和机器人聊了什么」，智伴需基于**真实的孩子与助手的对话记录**回答，禁止合理想象或编造。

为避免每次家长发消息都传全文聊天记录，采用 **zhiban-agent 按需主动拉取** 的方式。

---

## 一、数据流

```
家长问「宝宝最近聊了什么」
    │
    ▼
zhiban-agent 识别意图 → 需要孩子聊天记录
    │
    ▼
调用 manager-api GET /config/parent/child-chat-history?agentId=xx&macAddress=yy&limit=30
    │
    ▼
manager-api 从 ai_agent_chat_history 查询 → 返回格式化字符串
    │
    ▼
zhiban-agent 将记录注入上下文 → 生成真实汇总回答
```

---

## 二、manager-api 提供的接口

| 项目 | 说明 |
|------|------|
| **路径** | `GET /config/parent/child-chat-history` |
| **鉴权** | `Authorization: Bearer {server_secret}`（与智控台参数 `server_secret` 一致） |
| **参数** | `agentId`（必填）、`macAddress`（必填）、`limit`（可选，默认 30，最大 100） |
| **响应** | `{ "code": 0, "data": "孩子：xxx\n助手：yyy\n..." }`，无记录时 `data` 为空串 |

---

## 三、zhiban-agent 需要做的

### 3.1 入参来源

家长聊天请求中，`environment_context` 会包含：

- `agent_id`：智能体 ID
- `mac_address`：设备 MAC 地址（与 `ai_agent_chat_history` 存储一致）

zhiban-agent 在处理家长对话时，可从 `environment_context` 读取这两个参数。

### 3.2 拉取时机

当检测到家长询问**孩子近期聊天内容**时（如「你们最近聊了什么」「宝宝今天说了啥」等），再发起拉取；其他问题不拉取。

### 3.3 调用示例

```python
# 伪代码示例
async def fetch_child_chat_history(agent_id: str, mac_address: str, limit: int = 30) -> str:
    url = f"{MANAGER_API_BASE}/config/parent/child-chat-history"
    params = {"agentId": agent_id, "macAddress": mac_address, "limit": limit}
    headers = {"Authorization": f"Bearer {MANAGER_API_SECRET}"}
    resp = await httpx.get(url, params=params, headers=headers)
    data = resp.json()
    if data.get("code") == 0:
        return data.get("data") or ""
    return ""
```

### 3.4 回答约束

- 拉取到的记录为空时：明确回复「我暂时没有近期和 XXX 聊天的记录，等 TA 多和我聊几句后，你可以再来问我。」
- 有记录时：仅依据拉取到的真实对话做汇总，**禁止编造或推测**。

---

## 四、配置

zhiban-agent 需配置（与拉取 skill instructions 相同）：

- `MANAGER_API_BASE`：manager-api 地址，如 `http://manager-api:8080`
- `MANAGER_API_SECRET`：智控台「参数字典」中的 `server_secret`

---

## 五、前置条件

1. 智控台智能体配置中开启「聊天记录上报」（`chat_history_conf` 为 1 或 2）。
2. 设备有正确的 `mac_address`，且与 `ai_agent_chat_history` 中的存储一致（查询已兼容 `B6:C8:35:D6:10:48` 与 `b6_c8_35_d6_10_48` 等格式）。

---

## 六、拉取成功但 len=0（查不到记录）排查

当接口返回 `code: 0` 且 `len=0` 时，可按下列顺序排查：

| 可能原因 | 排查方法 |
|----------|----------|
| **1. 智能体未开启聊天记录**（最常见） | 智控台 → 智能体配置 → 「聊天记录」是否为 1 或 2（0 表示不上报）。为 0 时 xiaozhi-server 不会上报，永远查不到 |
| **2. 设备未实际上报对话** | 确认设备已连接并有过对话；检查 manager-api 日志是否有「设备 xxx 对应智能体 xxx 上报成功」 |
| **3. MAC 格式不一致** | manager-api 已支持 `B6:C8:35:D6:10:48` 与 `b6_c8_35_d6_10_48` 兼容查询，若仍无数据可查库确认 |
| **4. agent_id 不一致** | 设备更换智能体后，旧记录仍属原智能体，新 agent_id 查不到旧数据 |

**数据库快速核对**：

```sql
SELECT mac_address, agent_id, COUNT(*) 
FROM ai_agent_chat_history 
WHERE agent_id = '你的agentId'
GROUP BY mac_address, agent_id;
```
