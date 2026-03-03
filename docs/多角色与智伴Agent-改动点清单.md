# 多角色与智伴 Agent 适配 — 改动点清单（讨论后实施）

本文档列出《多角色与智伴Agent适配设计草案》涉及的所有**数据库**与**代码**改动点，讨论定稿后再按此实施。

---

## 一、数据库（manager-api）

### 1.1 新增表

| 表名 | 用途 | 主要字段（建议） |
|------|------|------------------|
| **ai_skill** | 技能定义（固定格式，类 Claude Skill） | `id`(PK), `name`, `description`, `instructions`(TEXT), `version`, `tools`(JSON), `metadata`(JSON), `create_time`, `update_time`；可选：`agent_id`(NULL 表示全局技能，非空表示仅该 agent 可用) |
| **ai_agent_skill_mapping** | 智能体维度「说话人类型 → 技能」映射 | `id`(PK), `agent_id`, `speaker_type`(owner_child/parent/other_child/other_adult/unknown), `skill_id`, `create_time`, `update_time`；唯一约束 (agent_id, speaker_type) |

说明：

- 若希望技能**全局复用**：ai_skill 不绑 agent_id，多个 agent 通过 ai_agent_skill_mapping 引用同一 skill_id。
- 若希望技能**仅某智能体可见**：可在 ai_skill 增加 agent_id，或通过 ai_agent_skill_mapping 仅配置该 agent 已选的 skill_id（需智控台技能列表按 agent 过滤）。

### 1.2 已有表（无结构变更，仅使用方式）

- **device_child**：已有 `birthday`，用于 xiaozhi 计算主孩子 `estimated_age`；私有配置需带出主孩子 id、birthday（见下）。
- **ai_agent_voice_print**：已有 `child_id`、`introduce`；speakers 列表已含 id, sourceName, introduce，无需改表。

---

## 二、manager-api 代码

### 2.1 配置下发（私有配置 getAgentModels）

| 文件/模块 | 改动内容 |
|-----------|----------|
| **ConfigServiceImpl** | ① 在 `getAgentModels` 内，根据 `device.getId()` 查 **device_child**，若存在主孩子则向 result 写入：`owner_child_id`（device_child.id）、`owner_child_voice_print_id`（该设备主孩子对应声纹的 id，用于 xiaozhi 判断 is_owner_child）、`owner_child_birthday`（或 `owner_child_age` 由后端算好下发，二选一）；② 查 **ai_agent_skill_mapping**（按 agent_id），拼出 `skill_mapping`：`{"owner_child":"skill_xxx", "parent":"skill_yyy", ...}` 写入 result。 |
| **ConfigController** | 无改动（仍通过 getAgentModels 返回）。 |

### 2.2 技能管理（智控台 CRUD）

| 类型 | 内容 |
|------|------|
| **实体 / DAO** | 新增 `AgentSkillEntity`（或 `SkillEntity`）、`AgentSkillMappingEntity`；对应 Dao、Mapper XML。 |
| **Service** | 新增 `AgentSkillService`：技能的增删改查；`AgentSkillMappingService`：按 agent_id 查/保存「speaker_type → skill_id」映射。 |
| **Controller** | 新增或挂到现有配置下：`GET/POST/PUT/DELETE /agent/skills`（技能列表/创建/更新/删除）；`GET/PUT /agent/{agentId}/skill-mapping`（某智能体的说话人类型→技能映射）。 |
| **权限** | 与现有智控台权限一致（需登录、操作智能体权限）。 |

### 2.3 智控台前端（manager-web）

| 页面/模块 | 改动内容 |
|-----------|----------|
| **技能管理** | 新增「技能管理」菜单/页：技能列表、新建技能（id/name/description/instructions/version/tools）、编辑、删除。 |
| **智能体编辑** | 在智能体编辑页增加「技能与说话人」配置区块：为 owner_child / parent / other_child / other_adult / unknown 各选一个技能（下拉从技能列表选），保存时调用 `PUT /agent/{agentId}/skill-mapping`。 |

---

## 三、xiaozhi-server 代码

### 3.1 私有配置解析与 conn 挂载

| 文件/模块 | 改动内容 |
|-----------|----------|
| **connection.py** | ① 在 `_background_initialize` / 拉取私有配置后，解析并挂到 conn：`owner_child_id`、`owner_child_birthday`（或 age）、`owner_child_voice_print_id`、`skill_mapping`（dict：speaker_type → skill_id）；② 增加 `current_speaker_id`（声纹 id，见下）；③ 增加 `current_round_speaker_type`（当前轮说话人类型，在 handle_voice_stop → startToChat 时设置，用于打断策略）。 |
| **config 解析** | 从 get_agent_models 返回的 result 中读取 `owner_child_*`、`skill_mapping`，写入 `conn.config` 或 conn 属性，供后续拼 speaker_context、skill_id 使用。 |

### 3.2 声纹识别结果带出 speaker_id

| 文件/模块 | 改动内容 |
|-----------|----------|
| **voiceprint_provider.py** | `identify_speaker` 改为返回**结构化结果**（如 `(speaker_id, speaker_name)` 或 `{speaker_id, speaker_name}`），便于下游同时拿到 id 与 name；未识别时返回 (None, "未知说话人") 或等价。 |
| **ASR base（handle_voice_stop）** | 接收声纹结构化结果，写入 `conn.current_speaker`、`conn.current_speaker_id`；enhanced_text 中可带 speaker_id（若需传给意图等）。 |
| **receiveAudioHandle.py** | 解析 JSON 时若有 speaker_id 则写入 `conn.current_speaker_id`，与现有 speaker_name 一致。 |

### 3.3 每轮 speaker_context、skill_id、user_id（role_id）

| 文件/模块 | 改动内容 |
|-----------|----------|
| **connection.py（chat）** | ① **role_id 计算**：根据 current_speaker_id、owner_child_voice_print_id 判断是否主孩子；主孩子则 `role_id = f"{device_id}_{child_id}"`（child_id 可用 device_child.id 或声纹 id，需与 manager 约定）；非主孩子则 `role_id = f"{device_id}_guest_{speaker_id}"` 或 `device_id_other`；未识别则 `role_id = device_id`。② **speaker_context 拼装**：从 voiceprint speaker_map 取 introduction（description）；主孩子用 birthday 算 estimated_age，非主孩子/未知从 introduce 抽或 "unknown"；speaker_type 根据是否主孩子/声纹库类型区分 owner_child、parent、other_child、other_adult、unknown。③ **skill_id**：从 conn.skill_mapping 按 current_round_speaker_type 取，若无则不带。④ 调用 `llm.response(..., user_id=role_id, speaker_context=..., skill_id=..., environment_context=...)`。 |
| **connection.py（memory）** | 每轮 chat 前按当前轮说话人设定 memory 的 role_id：**memory 基类增加 `switch_role(role_id)`**（或每轮在 query_memory 前设置 `memory.role_id = role_id`），再执行 `query_memory(query)`，保证记忆按人隔离。 |
| **providers/llm/ZhibanAgent/ZhibanAgent.py** | `response` / `response_with_functions` 从 kwargs 取 `user_id`、`speaker_context`、`skill_id`、`environment_context`，传给 `ZhibanAgentClient`。 |
| **zhibanAgent/zhiban_agent_client.py** | `chat`、`stream` 的 payload 增加：`user_id`、`speaker_context`（object）、`skill_id`（string）、`environment_context`（object）；均可选，未传则不带。 |

### 3.4 打断策略与打断意图识别

| 文件/模块 | 改动内容 |
|-----------|----------|
| **receiveAudioHandle / handleAudioMessage** | 当「当前轮为主孩子且机器未播」（如 `conn.client_is_speaking == False` 且 `conn.current_round_speaker_type == "owner_child"`）时，新进来的声音**不立即**触发 handleAbortMessage；先缓存或进入「待判定队列」。 |
| **新逻辑（打断判定）** | 对新声音先完成 ASR（若尚未有结果），得到文本后调用**打断意图识别**：关键词规则（如「暂停/停止/别说了/换个话题」）或轻量意图接口；若判定为打断意图，再执行 handleAbortMessage 并开启新轮处理新声音；否则丢弃/忽略该段。 |
| **意图模块 / 新接口** | 若用现有意图：增加「打断意图」分类或小模型/规则，返回 is_interrupt；或在 xiaozhi 内实现一简单函数 `is_interrupt_intent(text) -> bool`。 |

### 3.5 环境信息 environment_context

| 文件/模块 | 改动内容 |
|-----------|----------|
| **connection.py** | 在调用 zhiban 前组装 **environment_context**：`noise_level`（xiaozhi 根据 VAD/ASR 自算或 unknown）、`has_overlapping_voice`（本轮是否多人/重叠）、`scene`、`environment_description`、`device_reported_context`；后三项来自**设备上报**。 |
| **设备上报约定** | 约定设备在 **hello 消息**或某类上行消息中可带字段：如 `scene`（indoor/outdoor/car/night）、`environment_description`（图像识别文案）、`device_reported_context`（任意键值）；xiaozhi 解析后存到 conn 或当轮变量，拼入 environment_context。 |
| **helloHandle / 消息解析** | 若 hello 消息 body 扩展：解析 `scene`、`environment_description`、`device_reported_context`，写入 conn 属性，供 chat 时读取。 |

### 3.6 Memory 按人隔离与访客短期

| 文件/模块 | 改动内容 |
|-----------|----------|
| **providers/memory/base.py** | 增加 `switch_role(self, role_id)`，实现为 `self.role_id = role_id`，供每轮 chat 前切换。 |
| **providers/memory/short_long_memory、mem0ai 等** | 若 save_memory 会按 role_id 写入：对 `device_id_guest_*` / `device_id_other` 采用**只写短期、不写长期**的策略（具体由 zhiban 或本端 memory 实现约定）。xiaozhi 侧仅保证 role_id 正确传入。 |

---

## 四、zhiban-agent（若独立仓库）

以下为 zhiban-agent 侧需具备的能力，不在本仓库实现则需在彼端落地：

| 项目 | 内容 |
|------|------|
| **请求体** | 接收 `user_id`、`speaker_context`、`skill_id`、`environment_context`；与 xiaozhi 约定字段一致。 |
| **Skill 加载** | 根据 `skill_id` 加载对应 instructions（及 tools）；若 skill 定义在智控台，zhiban 需能拉取或同步（如从 manager-api 拉取该 agent 的 skill 列表 + mapping）。 |
| **System 注入** | 将 speaker_context（说话人、类型、简介、年龄、is_owner_child）、environment_context（噪音、场景、环境描述等）注入 system 或首条上下文。 |
| **Memory** | 按 user_id 分区；对访客（如 user_id 含 guest/other）只写短期、不参与长期召回。 |

---

## 五、改动点汇总表（按模块）

| 模块 | 数据库 | 后端代码 | 前端/其他 |
|------|--------|----------|----------|
| **manager-api** | 新增 ai_skill、ai_agent_skill_mapping | ConfigServiceImpl 下发 owner_child_*、skill_mapping；Skill CRUD、SkillMapping CRUD | manager-web：技能管理页、智能体编辑页「说话人→技能」 |
| **xiaozhi-server** | — | connection 解析配置、拼 speaker_context/skill_id/role_id、memory switch_role；voiceprint 返回 speaker_id；ASR/conn 带 current_speaker_id；打断意图识别；environment_context 组装；zhiban_agent_client 扩展 payload；hello 解析环境字段 | 设备端约定：hello/上报带 scene、environment_description 等 |
| **zhiban-agent** | — | 接收新字段、按 skill_id 加载 instructions、按 user_id 分区 memory、访客短期策略 | — |

---

## 六、实施顺序建议（与设计稿一致）

1. **数据库 + 配置下发**：建表 ai_skill、ai_agent_skill_mapping；ConfigServiceImpl 增加 owner_child_*、skill_mapping 下发。  
2. **xiaozhi 拼 context + 传 zhiban**：声纹带 speaker_id、conn 挂 owner_child/skill_mapping、chat 里拼 speaker_context/skill_id/user_id、zhiban_client 扩展 payload；memory switch_role、role_id 按人计算。  
3. **打断意图**：主孩子轮次内新声音先 ASR 再打断意图识别，是则打断。  
4. **智控台技能与映射**：Skill CRUD、智能体页「说话人→技能」配置。  
5. **环境信息**：xiaozhi 组装 environment_context；设备 hello/上报约定与解析；zhiban 注入环境描述。

讨论定稿后，可按上表逐项实施；有争议的项可在本清单上标注「待定」再细化。
