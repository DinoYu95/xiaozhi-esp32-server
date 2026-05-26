package xiaozhi.modules.parent.service.impl;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.utils.JsonUtils;
import xiaozhi.modules.agent.service.AgentSkillService;
import xiaozhi.modules.agent.vo.AgentSkillVO;
import xiaozhi.modules.llm.dto.LlmOpenAiCallConfig;
import xiaozhi.modules.llm.service.LLMService;
import xiaozhi.modules.model.entity.ModelConfigEntity;
import xiaozhi.modules.model.service.ModelConfigService;
import xiaozhi.modules.parent.dto.ParentSkillDraftFields;
import xiaozhi.modules.parent.service.ParentSkillAssistService;
import xiaozhi.modules.parent.vo.ParentSkillDraftVO;
import xiaozhi.modules.sys.service.SysParamsService;

@Slf4j
@Service
@RequiredArgsConstructor
public class ParentSkillAssistServiceImpl implements ParentSkillAssistService {

    private static final String PARAM_KEY = "server.parent_skill_assist_config";

    private static final Pattern JSON_OBJECT = Pattern.compile("\\{.*\\}", Pattern.DOTALL);
    private static final int NAME_MAX = 128;
    private static final int DESC_MAX = 512;
    private static final int INSTRUCTIONS_MAX = 4000;

    private static final String PROMPT_TEMPLATE =
            """
            你是「儿童陪伴机器人」的对话能力设计助手。家长不懂 prompt，只会用口语描述期望；你需要将其转化为标准「技能」配置。

            ## 重要：技能 ≠ 任务
            - 技能：孩子**主动和机器人聊天**时，系统根据**对话意图**自动选用的一种**聊天方式/陪伴模式**（被动响应、随聊随用）。
            - 不是：家长下发的限时任务、到点提醒、作业布置、远程指挥机器人去做某事。
            - instructions 必须写「当孩子在对话中提到/询问…时，机器人应…」，禁止写「请在 XX 时间提醒」「去监督孩子完成」等任务式表述。

            ## 技能配置三部分
            1. name：能力名称，2~12 字，如「睡前故事」「英语闲聊」（不要用「任务」「下发」等词）
            2. description：给家长看的一句话，说明「孩子聊到什么时会怎样」，20~80 字
            3. instructions：发给大模型的技能指令，须包含：
               - **触发场景**：孩子在对话里说什么、问什么时会启用（关键词/意图示例）
               - 对话语气与风格（温柔/活泼/鼓励等）
               - 内容边界（适合儿童、积极安全）
               - 具体聊天行为（怎么接话、故事长度、是否互动提问等）
               - 长度约 200~800 字，中文，不要用 markdown 标题

            ## 官方技能格式参考（仅供风格参考，勿照抄）
            {examples}

            ## 输出要求
            只输出一个 JSON 对象，不要 markdown 代码块，不要任何解释文字：
            {"name":"...","description":"...","triggerHint":"当孩子说/问到…时","instructions":"..."}

            ## 家长描述
            {user_intent}
            {refinement_block}
            """;

    private final LLMService llmService;
    private final AgentSkillService agentSkillService;
    private final SysParamsService sysParamsService;
    private final ModelConfigService modelConfigService;

    @Data
    private static final class AssistCfg {
        private boolean enabled = true;
        /** ai_model_config.id；与 baseUrl+apiKey 二选一，优先 llmModelId */
        private String llmModelId = "";
        /** OpenAI 兼容直连（camelCase 或 snake_case 均可） */
        private String baseUrl = "";
        private String apiKey = "";
        private String modelName = "";
        private Double temperature;
        private Integer maxTokens;
    }

    @Override
    public ParentSkillDraftVO generateDraft(
            String userIntent, String refinement, ParentSkillDraftFields previousDraft) {
        AssistCfg cfg = loadCfg();
        if (!cfg.isEnabled()) {
            throw new RenException("AI 生成对话能力已关闭，请在智控台参数字典开启 server.parent_skill_assist_config，或手动填写");
        }

        String prompt = buildPrompt(userIntent, refinement, previousDraft);
        String raw;
        String logSource;

        if (StringUtils.isNotBlank(StringUtils.trimToEmpty(cfg.getLlmModelId()))) {
            String modelId = resolveLlmModelId(cfg);
            if (!llmService.isAvailable(modelId)) {
                throw new RenException(
                        "AI 服务不可用：参数字典 llmModelId="
                                + modelId
                                + " 未启用或缺少 base_url/api_key");
            }
            logSource = "llmModelId=" + modelId;
            raw = llmService.generateSummary("", prompt, modelId);
        } else if (hasInlineLlm(cfg)) {
            LlmOpenAiCallConfig inline = toInlineConfig(cfg);
            if (!llmService.isInlineConfigAvailable(inline)) {
                throw new RenException("参数字典 server.parent_skill_assist_config 缺少 baseUrl 与 apiKey");
            }
            logSource = "inline baseUrl=" + StringUtils.abbreviate(inline.getBaseUrl(), 48);
            raw = llmService.chatWithOpenAiConfig(prompt, inline);
        } else {
            if (!llmService.isAvailable()) {
                throw new RenException(
                        "AI 服务暂不可用：请在参数字典配置 llmModelId 或 baseUrl+apiKey，或在模型配置中启用默认 LLM");
            }
            logSource = "defaultLlm";
            raw = llmService.generateSummary("", prompt, null);
        }

        log.info("parent skill draft LLM call source={} userIntentLen={}", logSource, userIntent == null ? 0 : userIntent.length());
        if (StringUtils.isBlank(raw) || raw.contains("生成总结失败") || raw.contains("LLM服务不可用")) {
            log.warn("parent skill draft LLM failed, raw={}", StringUtils.abbreviate(raw, 120));
            throw new RenException("AI 生成技能失败，请稍后重试或手动填写");
        }

        ParentSkillDraftVO vo = parseDraft(raw);
        if (vo == null) {
            log.warn("parent skill draft parse failed, raw={}", StringUtils.abbreviate(raw, 200));
            throw new RenException("AI 返回格式异常，请重试或手动填写");
        }
        return vo;
    }

    private AssistCfg loadCfg() {
        String json = sysParamsService.getValue(PARAM_KEY, true);
        AssistCfg c = new AssistCfg();
        if (StringUtils.isBlank(json)) {
            return c;
        }
        try {
            AssistCfg parsed = JsonUtils.parseObject(json, AssistCfg.class);
            if (parsed != null) {
                c = parsed;
            }
            JSONObject o = JSONUtil.parseObj(json);
            if (StringUtils.isBlank(c.getBaseUrl())) {
                c.setBaseUrl(StringUtils.trimToEmpty(o.getStr("base_url")));
            }
            if (StringUtils.isBlank(c.getApiKey())) {
                c.setApiKey(StringUtils.trimToEmpty(o.getStr("api_key")));
            }
            if (StringUtils.isBlank(c.getModelName())) {
                c.setModelName(StringUtils.trimToEmpty(o.getStr("model_name")));
            }
            if (c.getMaxTokens() == null && o.getInt("max_tokens") != null) {
                c.setMaxTokens(o.getInt("max_tokens"));
            }
            return c;
        } catch (Exception e) {
            log.warn("解析 {} 失败: {}", PARAM_KEY, e.getMessage());
        }
        return c;
    }

    private static boolean hasInlineLlm(AssistCfg cfg) {
        return StringUtils.isNotBlank(cfg.getBaseUrl()) && StringUtils.isNotBlank(cfg.getApiKey());
    }

    private static LlmOpenAiCallConfig toInlineConfig(AssistCfg cfg) {
        LlmOpenAiCallConfig c = new LlmOpenAiCallConfig();
        c.setBaseUrl(cfg.getBaseUrl().trim());
        c.setApiKey(cfg.getApiKey().trim());
        c.setModelName(StringUtils.trimToEmpty(cfg.getModelName()));
        c.setTemperature(cfg.getTemperature());
        c.setMaxTokens(cfg.getMaxTokens());
        return c;
    }

    private String resolveLlmModelId(AssistCfg cfg) {
        String id = StringUtils.trimToEmpty(cfg.getLlmModelId());
        if (StringUtils.isBlank(id)) {
            return null;
        }
        ModelConfigEntity model = modelConfigService.getModelByIdFromCache(id);
        if (model == null) {
            throw new RenException("参数字典 " + PARAM_KEY + " 的 llmModelId 不存在: " + id);
        }
        if (!"LLM".equalsIgnoreCase(StringUtils.trimToEmpty(model.getModelType()))) {
            throw new RenException("参数字典 " + PARAM_KEY + " 的 llmModelId 须为 LLM 类型模型，当前为: " + model.getModelType());
        }
        if (model.getIsEnabled() == null || model.getIsEnabled() != 1) {
            throw new RenException("参数字典指定的 LLM 未启用: " + id);
        }
        return id;
    }

    private String buildPrompt(String userIntent, String refinement, ParentSkillDraftFields previousDraft) {
        String examples = buildExamples();
        String refinementBlock = "";
        if (previousDraft != null
                && (StringUtils.isNotBlank(previousDraft.getName())
                        || StringUtils.isNotBlank(previousDraft.getInstructions()))) {
            refinementBlock =
                    "\n## 上一轮草稿\n"
                            + JSONUtil.toJsonStr(previousDraft)
                            + "\n## 修改意见\n"
                            + StringUtils.defaultIfBlank(refinement, "请优化上一轮草稿");
        } else if (StringUtils.isNotBlank(refinement)) {
            refinementBlock = "\n## 补充要求\n" + refinement.trim();
        }

        return PROMPT_TEMPLATE.replace("{examples}", examples)
                .replace("{user_intent}", userIntent.trim())
                .replace("{refinement_block}", refinementBlock);
    }

    private String buildExamples() {
        List<AgentSkillVO> official = agentSkillService.listOfficialRecommended();
        if (official == null || official.isEmpty()) {
            return "（暂无官方示例）";
        }
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (AgentSkillVO s : official) {
            if (n >= 2) {
                break;
            }
            if (StringUtils.isAnyBlank(s.getName(), s.getInstructions())) {
                continue;
            }
            sb.append("- 名称：").append(s.getName()).append("\n");
            sb.append("  描述：").append(StringUtils.defaultString(s.getDescription())).append("\n");
            sb.append("  指令摘要：")
                    .append(StringUtils.abbreviate(s.getInstructions().replace('\n', ' '), 200))
                    .append("\n\n");
            n++;
        }
        return sb.length() > 0 ? sb.toString().trim() : "（暂无官方示例）";
    }

    private ParentSkillDraftVO parseDraft(String raw) {
        String json = extractJson(raw);
        if (json == null) {
            return null;
        }
        try {
            JSONObject obj = JSONUtil.parseObj(json);
            String name = StringUtils.trimToEmpty(obj.getStr("name"));
            String description = StringUtils.trimToEmpty(obj.getStr("description"));
            String instructions = StringUtils.trimToEmpty(obj.getStr("instructions"));
            String triggerHint = StringUtils.trimToEmpty(obj.getStr("triggerHint"));
            if (StringUtils.isAnyBlank(name, instructions)) {
                return null;
            }
            ParentSkillDraftVO vo = new ParentSkillDraftVO();
            vo.setName(truncate(name, NAME_MAX));
            vo.setDescription(truncate(description, DESC_MAX));
            vo.setInstructions(truncate(instructions, INSTRUCTIONS_MAX));
            vo.setTriggerHint(truncate(
                    StringUtils.defaultIfBlank(triggerHint, description), 120));
            return vo;
        } catch (Exception e) {
            log.debug("parse skill draft json error: {}", e.getMessage());
            return null;
        }
    }

    private static String extractJson(String raw) {
        String trimmed = raw.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "").trim();
        }
        if (JSONUtil.isTypeJSONObject(trimmed)) {
            return trimmed;
        }
        Matcher m = JSON_OBJECT.matcher(trimmed);
        if (m.find()) {
            return m.group();
        }
        return null;
    }

    private static String truncate(String s, int max) {
        if (s == null || s.length() <= max) {
            return s;
        }
        return s.substring(0, max);
    }
}
