# 作业辅导模式

会话级专注模式：孩子通过唤醒词进入后，仅处理作业/学习相关问题；说「退出作业辅导」退出。

## 唤醒词

| 动作 | 短语示例 |
|------|----------|
| 进入 | 进入作业辅导、作业辅导模式、帮我辅导作业、我要写作业了 |
| 退出 | 退出作业辅导、结束作业辅导、不辅导了 |

## 链路

```
孩子说「进入作业辅导」
  → xiaozhi-server 识别唤醒词，设置 conn.active_mode=homework_tutor，TTS 确认
  → 后续对话 environment_context.active_mode 传给 zhiban-agent
  → zhiban 注入作业辅导 system prompt，拒绝无关话题
  → 需要看题时走拍照返图 + 多模态 LLM（homework purpose）
孩子说「退出作业辅导」
  → xiaozhi 清除 active_mode，TTS 告别，不走 LLM
```

## xiaozhi-server

| 文件 | 说明 |
|------|------|
| `core/zhibanAgent/homework_tutor_mode.py` | 状态机、唤醒词、超时 |
| `core/handle/receiveAudioHandle.py` | 进入/退出 fast path |
| `core/connection.py` | `active_mode` 字段、`environment_context` 透传 |

可选配置（`data/.config.yaml`）：

```yaml
homework_tutor_mode:
  idle_timeout_sec: 1800   # 默认 30 分钟无操作自动退出
```

## zhiban-agent

| 文件 | 说明 |
|------|------|
| `app/builtin_homework_tutor.py` | 作业辅导 prompt、无关话题拦截 |
| `app/main.py` | system 注入 + 无关话题快速拒绝 |
| `app/llm/device_tools_runner.py` | 作业场景拍照意图、vision prompt |

## 看题两步流程

```
孩子：帮我看看这道题
设备：好，把不会的那道题举到摄像头前面…准备好了说「好了」或「帮我拍照」

孩子：好了
设备：（拍照 → 多模态识别 → 分步引导）
```

孩子说「拍一张照片」等明确拍照指令时，跳过引导直接拍。

## 验收用例

| # | 场景 | 预期 |
|---|------|------|
| 1 | 说「进入作业辅导」 | 进入确认 TTS，不调用 LLM |
| 2 | 模式中问「25乘16怎么算」 | 引导式辅导，不给直接答案 |
| 3 | 模式中说「讲个故事」 | 温和拒绝，提示退出 |
| 4 | 说「退出作业辅导」 | 退出 TTS，恢复日常 |
| 5 | 未进入时说「退出作业辅导」 | 提示当前不在模式中 |
| 6 | 模式中说「帮我看看这道题」 | 先引导摆题，**不拍照** |
| 7 | 引导后说「好了」 | 触发拍照 + 作业向 vision 回复 |
| 8 | 模式中说「拍一张照片」 | 跳过引导，直接拍照 |

## 相关文档

- 拍照返图：`docs/zhiban-agent-device-mcp-image联调说明.md`
- 固件协议：`docs/esp32-mcp-camera-image-only.md`
