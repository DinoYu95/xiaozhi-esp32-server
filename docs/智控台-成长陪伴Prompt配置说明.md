# 智控台 · 成长陪伴 Prompt 配置说明

设备使用智伴（ZhibanAgent）时，可在智控台配置**全局统一**的成长陪伴说明：`manager-api` 在设备拉取配置（`getAgentModels`）时用当前设备的**主孩子档案**（`device_child`）替换占位符，得到 `companion_growth_prompt` 下发给 `xiaozhi-server`，再在调用 zhiban 时写入 `environment_context` 并缀在用户话术前，便于模型稳定遵守。

## 在哪里配置

1. 登录智控台 **参数管理**（超级管理员）。
2. 找到 **`server.agent_companion_growth_prompt_template`**（若数据库尚未迁移，可先执行变更集 `202604031200.sql` 或等待 Liquibase 自动执行）。

## 配置内容是什么

- **参数编码**：`server.agent_companion_growth_prompt_template`
- **类型**：字符串（可换行、可多段）
- **含义**：全站共用的智伴「成长陪伴 / 对话风格」说明模板；保存后建议**刷新服务端配置缓存**（若你们有「重载参数」操作）或重启 `manager-api`，以免仍用旧值。

## 可用占位符（由服务端自动替换）

来自 `device_child`（小程序填写的主孩子）；若无主孩子，对应位置会替换成**空字符串**（模板里可写「若信息为空则……」）。

| 占位符 | 说明 |
|--------|------|
| `{child_name}` | 昵称/姓名 |
| `{child_age_years}` | 按生日推算的整岁，无生日则为空 |
| `{child_birthday}` | 生日 `yyyy-MM-dd`，无则为空 |
| `{age_stage}` | 年龄段说明 |
| `{hobbies}` | 爱好 |
| `{favorite_topics}` | 喜欢的话题 |
| `{favorite_stories}` | 喜欢的故事/绘本 |
| `{personality_note}` | 性格/偏好备注 |
| `{school}` | 学校/幼儿园 |

占位符采用**简单全文替换**，请勿让不同占位符互相包含相同子串；新增占位符需改 `ConfigServiceImpl.buildCompanionGrowthPrompt`。

## 默认模板

迁移文件 `main/manager-api/src/main/resources/db/changelog/202604031200.sql` 中带有一条默认中文模板，可按产品与运营需要直接在参数管理中修改。

## 调用链简述

`sys_params` 模板 + `device_child` → `ConfigServiceImpl.buildCompanionGrowthPrompt` → 接口字段 `companion_growth_prompt` → `xiaozhi-server` `config` + `environment_context` → `ZhibanAgent` 请求 zhiban（文本前缀 + JSON 中均带，便于 zhiban 侧再加工）。

## 如何从日志确认「有没有带上」

### xiaozhi-server（连接拉配置时）

设备连上并成功拉取私有配置后，在 **connection** 相关日志里搜：

- **`配置拉取: companion_growth_prompt 已写入，长度=`** → 已从 manager-api 收到并写入 `self.config`。
- **`配置拉取: 无 companion_growth_prompt`** → 接口没带回该字段，或智控台模板为 `null`/空（检查 manager-api 版本、Liquibase、`server.agent_companion_growth_prompt_template`）。

注：`异步获取差异化配置成功` 后打印的整段 JSON 里理论上也有 `companion_growth_prompt`，但字段多易被忽略，以上两行是专用提示。

调 DEBUG 时还可看到：**`environment_context 含 companion_growth_prompt`**（每轮对话拼装上下文时）。

### zhiban 调用前（xiaozhi-server）

在 **xiaozhi-server** 日志中搜索（模块 `core.zhibanAgent.zhiban_agent_client`）：

- **INFO**：`zhiban-agent 流式:` 或 `zhiban-agent 非流式:` 后字段含义  
  - **`含成长陪伴前缀=true`**：发给 zhiban 的 **`text` 里已包含** `【成长陪伴与对话风格】` 段落（最可靠，不依赖 zhiban 是否解析 JSON）。  
  - **`companion_env长度=N`**：`environment_context` 里 **`companion_growth_prompt`** 字符数；N&gt;0 表示 HTTP JSON body 里也带了同一段，供 zhiban 服务端拼装 system 使用。  
- **DEBUG**：若 `companion_env长度>0`，会再打 **`companion_growth_prompt` 前 120 字**（需把该 logger 级别调到 DEBUG）。

另：`core.providers.llm.ZhibanAgent.ZhibanAgent` 在注入成功时会有 **`注入 companion_growth_prompt，长度=…`**（INFO）。

若 **`含成长陪伴前缀=false`** 且 **`companion_env长度=0`**：多为设备未拉到配置（无 `companion_growth_prompt`）、或智控台模板为空 / `null`、或该设备无主孩子且模板被删空。

---

## zhiban-agent 侧（同目录 `zhiban-agent` 项目）

`_build_system_message` 会读取 **`environment_context["companion_growth_prompt"]`**，写入 system 中的 **「## 成长陪伴与对话风格」** 段落（在「长期记忆」之后、「当前说话人」之前）。同时 xiaozhi 仍可能把同一段缀在 **`text` 前**，二者内容一致时不影响正确性，仅多占少量 token；若后续要减冗长可只保留一侧。

zhiban 日志：**`chat request: ... companion_growth_len=N`**（N&gt;0 表示 JSON 里带了成长陪伴）；**`_build_system_message: companion_growth_prompt len=N`** 表示已写入本轮 system。
