# test_page 聊天记录测试说明

## 问题现象

使用 test_page 对话测试时，数据库 `ai_agent_chat_history` 表中没有聊天记录写入。

## 根因分析

### 1. test_page 默认使用随机 MAC

首次打开 test_page 时，若未保存过设备 MAC，会**自动生成随机 MAC**（如 `A1:B2:C3:D4:E5:F6`）。

该随机 MAC 从未在智控台中添加，因此：
- `ai_device` 表中没有对应记录
- 设备未绑定任何 Agent

### 2. 上报链路依赖 ai_device

聊天记录上报流程：

1. **xiaozhi-server** 在 ASR/TTS 完成后，调用 manager-api `/agent/chat-history/report`
2. **manager-api** 通过 `getDefaultAgentByMacAddress(macAddress)` 查询：
   ```sql
   SELECT a.* FROM ai_device d
   LEFT JOIN ai_agent a ON d.agent_id = a.id
   WHERE d.mac_address = #{macAddress}
   ```
3. 若 `ai_device` 中无该 MAC，返回 `null` → **直接返回 false，不写入数据库**

### 3. xiaozhi-server 侧也会拦截

在 `_initialize_private_config_async` 中：
- 调用 `get_private_config_from_api` 获取配置
- 若设备不存在 → 抛出 `DeviceNotFoundException` → `need_bind = True`
- `need_bind = True` 时，上报线程不会启动，`enqueue_asr_report` / `enqueue_tts_report` 直接 return

因此，使用未在智控台添加的设备 MAC 时，聊天记录根本不会发起上报。

## 解决步骤

### 步骤 1：在智控台添加设备

1. 登录智控台
2. 进入「设备管理」→「手动添加设备」
3. 填写：
   - **MAC 地址**：格式如 `B6:C8:35:D6:10:48`（后续 test_page 需使用相同值）
   - **关联 Agent**：选择要测试的智能体
4. 保存

### 步骤 2：配置 Agent 聊天记录

1. 在「角色配置」中为该 Agent 设置：
   - **聊天记录**：选择「仅记录文本」或「文本+音频」
   - 若选择「不记录」，则不会写入 `ai_agent_chat_history`

### 步骤 3：在 test_page 中使用相同 MAC

1. 打开 test_page 设置弹窗
2. 在「设备 MAC」输入框中填入**与智控台中完全一致的 MAC 地址**
3. 保存并拨号连接
4. 进行对话测试

对话完成后，在 `ai_agent_chat_history` 表中即可看到对应记录。

## 验证方式

```sql
SELECT * FROM ai_agent_chat_history
WHERE mac_address = '你填入的MAC'
ORDER BY created_at DESC
LIMIT 20;
```

## 已做的改进

1. **test_page**：不再默认生成随机 MAC，未保存时留空，并增加提示文案
2. **设备 MAC 输入框**：增加 placeholder 与说明：「需先在智控台「手动添加设备」并绑定 Agent，再填入相同 MAC，否则聊天记录无法写入」
