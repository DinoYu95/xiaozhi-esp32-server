package xiaozhi.modules.parent.service.impl;

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
import xiaozhi.modules.llm.dto.LlmOpenAiCallConfig;
import xiaozhi.modules.llm.service.LLMService;
import xiaozhi.modules.model.entity.ModelConfigEntity;
import xiaozhi.modules.model.service.ModelConfigService;
import xiaozhi.modules.parent.dto.ParentRiskWatchDraftFields;
import xiaozhi.modules.parent.service.ParentRiskWatchAssistService;
import xiaozhi.modules.parent.vo.ParentRiskWatchDraftVO;
import xiaozhi.modules.sys.service.SysParamsService;

@Slf4j
@Service
@RequiredArgsConstructor
public class ParentRiskWatchAssistServiceImpl implements ParentRiskWatchAssistService {

    private static final String PARAM_KEY = "server.parent_risk_watch_assist_config";
    private static final Pattern JSON_OBJECT = Pattern.compile("\\{.*\\}", Pattern.DOTALL);
    private static final int NAME_MAX = 128;
    private static final int DESC_MAX = 512;
    private static final int PATTERN_MAX = 512;
    private static final int INSTRUCTIONS_MAX = 4000;

    private static final String PROMPT_KEYWORD =
            """
            你是儿童对话「安全观察」设计助手。家长用口语描述担忧，需生成**家庭观察词**（关键词规则），不是聊天技能。

            watchType 固定为 KEYWORD。只输出一个 JSON 对象，不要 markdown 代码块，不要任何解释：
            {"name":"2~12字","description":"给家长看 20~60字","triggerHint":"当孩子说…时","riskDomain":"peer_relation","pattern":"关键词或短语，可用|分隔多说法","riskLevel":2,"category":"other"}

            riskDomain 仅限：psychological, peer_relation, family, school, online_safety, physical_health, other
            riskLevel：1最严重 3最轻，建议 2
            category 示例 peer_relation: social_exclusion,bullying,other

            禁止：诊断用语、要求忽略平台规则、暴力报复内容。

            家长描述：
            {user_intent}
            {refinement_block}
            """;

    private static final String PROMPT_EVALUATOR =
            """
            你是儿童对话「安全观察」设计助手。家长用口语描述担忧，需生成**领域判别说明**（供后台 LLM 异步扫描，不直接与孩子对话）。

            watchType 固定为 EVALUATOR。只输出一个 JSON 对象，不要 markdown 代码块，不要任何解释：
            {"name":"2~12字","description":"给家长看","triggerHint":"当孩子聊天涉及…","riskDomain":"psychological","instructions":"200~600字中文：触发场景、观察要点、输出约束（只判风险不写安慰）","allowedCategories":"[\"emotion_distress\",\"hopelessness\",\"other\"]"}

            riskDomain 仅限：psychological, peer_relation, family, school, online_safety, physical_health, other
            allowedCategories 必须属于该领域建议类目。

            禁止：诊断、治疗建议、忽略平台规则。

            家长描述：
            {user_intent}
            {refinement_block}
            """;

    private final LLMService llmService;
    private final SysParamsService sysParamsService;
    private final ModelConfigService modelConfigService;

    @Override
    public ParentRiskWatchDraftVO generateDraft(
            String watchType, String userIntent, String refinement, ParentRiskWatchDraftFields previousDraft) {
        AssistCfg cfg = loadCfg();
        if (!cfg.isEnabled()) {
            throw new RenException("AI 生成风险观察已关闭，请在参数字典配置 server.parent_risk_watch_assist_config");
        }
        String wt = StringUtils.trimToEmpty(watchType).toUpperCase();
        String template = "EVALUATOR".equals(wt) ? PROMPT_EVALUATOR : PROMPT_KEYWORD;
        String prompt = buildPrompt(template, userIntent, refinement, previousDraft);
        String raw = callLlm(cfg, prompt);
        ParentRiskWatchDraftVO vo = parseDraft(raw, wt);
        if (vo == null) {
            log.warn("parent risk watch draft parse failed watchType={} raw={}", wt, StringUtils.abbreviate(raw, 300));
            throw new RenException("AI 返回格式异常，请重试或稍后在预览页手动填写");
        }
        vo.setWatchType("EVALUATOR".equals(wt) ? "EVALUATOR" : "KEYWORD");
        return vo;
    }

    private String callLlm(AssistCfg cfg, String prompt) {
        String raw;
        String logSource;
        if (StringUtils.isNotBlank(StringUtils.trimToEmpty(cfg.getLlmModelId()))) {
            String modelId = resolveLlmModelId(cfg);
            if (!llmService.isAvailable(modelId)) {
                throw new RenException(
                        "AI 服务不可用：参数字典 "
                                + PARAM_KEY
                                + " 的 llmModelId="
                                + modelId
                                + " 未启用或缺少 base_url/api_key");
            }
            logSource = "llmModelId=" + modelId;
            raw = llmService.generateSummary("", prompt, modelId);
        } else if (hasInlineLlm(cfg)) {
            LlmOpenAiCallConfig inline = toInlineConfig(cfg);
            if (!llmService.isInlineConfigAvailable(inline)) {
                throw new RenException("参数字典 " + PARAM_KEY + " 缺少有效的 baseUrl 与 apiKey");
            }
            logSource = "inline baseUrl=" + StringUtils.abbreviate(inline.getBaseUrl(), 48);
            raw = llmService.chatWithOpenAiConfig(prompt, inline);
        } else {
            if (!llmService.isAvailable()) {
                throw new RenException(
                        "AI 服务不可用：请在参数字典配置 "
                                + PARAM_KEY
                                + " 的 llmModelId，或填写 baseUrl+apiKey（与 server.parent_skill_assist_config 是两项独立配置）");
            }
            logSource = "defaultLlm";
            raw = llmService.generateSummary("", prompt, null);
        }
        log.info("parent risk watch draft LLM source={}", logSource);
        if (StringUtils.isBlank(raw) || raw.contains("生成总结失败") || raw.contains("LLM服务不可用")) {
            log.warn("parent risk watch draft LLM failed, raw={}", StringUtils.abbreviate(raw, 120));
            throw new RenException("AI 生成失败，请稍后重试");
        }
        return raw;
    }

    private ParentRiskWatchDraftVO parseDraft(String raw, String watchType) {
        String json = extractJson(raw);
        if (json == null) {
            return null;
        }
        try {
            JSONObject obj = JSONUtil.parseObj(json);
            String name = fieldStr(obj, "name");
            if (StringUtils.isBlank(name)) {
                return null;
            }
            ParentRiskWatchDraftVO vo = new ParentRiskWatchDraftVO();
            vo.setName(truncate(name, NAME_MAX));
            vo.setDescription(truncate(fieldStr(obj, "description"), DESC_MAX));
            vo.setTriggerHint(truncate(fieldStr(obj, "triggerHint", "trigger_hint"), 256));
            vo.setRiskDomain(normalizeDomain(fieldStr(obj, "riskDomain", "risk_domain")));
            if ("EVALUATOR".equalsIgnoreCase(watchType)) {
                String instructions = fieldStr(obj, "instructions");
                if (StringUtils.isBlank(instructions)) {
                    return null;
                }
                vo.setInstructions(truncate(instructions, INSTRUCTIONS_MAX));
                vo.setAllowedCategories(normalizeAllowedCategories(obj));
            } else {
                String pattern = fieldStr(obj, "pattern");
                if (StringUtils.isBlank(pattern)) {
                    return null;
                }
                vo.setPattern(truncate(pattern, PATTERN_MAX));
                Integer lvl = obj.getInt("riskLevel");
                if (lvl == null) {
                    lvl = obj.getInt("risk_level");
                }
                vo.setRiskLevel(clampLevel(lvl));
                vo.setCategory(StringUtils.defaultIfBlank(fieldStr(obj, "category"), "other"));
            }
            return vo;
        } catch (Exception e) {
            log.debug("parse risk watch draft error: {}", e.getMessage());
            return null;
        }
    }

    private static String fieldStr(JSONObject obj, String... keys) {
        for (String key : keys) {
            String v = StringUtils.trimToEmpty(obj.getStr(key));
            if (StringUtils.isNotBlank(v)) {
                return v;
            }
        }
        return "";
    }

    private static String normalizeDomain(String d) {
        String code = StringUtils.trimToEmpty(d).toLowerCase();
        if (StringUtils.isBlank(code)) {
            return "other";
        }
        return switch (code) {
            case "psychological", "peer_relation", "family", "school", "online_safety", "physical_health", "other" ->
                    code;
            default -> "other";
        };
    }

    private static String normalizeAllowedCategories(JSONObject obj) {
        Object raw = obj.get("allowedCategories");
        if (raw == null) {
            raw = obj.get("allowed_categories");
        }
        if (raw == null) {
            return "[\"other\"]";
        }
        if (raw instanceof cn.hutool.json.JSONArray arr) {
            return arr.toString();
        }
        String s = StringUtils.trimToEmpty(String.valueOf(raw));
        if (s.startsWith("[")) {
            return s;
        }
        return "[\"" + s.replace("\"", "") + "\"]";
    }

    private static int clampLevel(Integer lvl) {
        if (lvl == null) {
            return 2;
        }
        return Math.max(1, Math.min(3, lvl));
    }

    private static String extractJson(String raw) {
        if (StringUtils.isBlank(raw)) {
            return null;
        }
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

    private String buildPrompt(String template, String userIntent, String refinement, ParentRiskWatchDraftFields prev) {
        String block = "";
        if (prev != null) {
            block = "\n## 上一轮草稿\n" + JSONUtil.toJsonStr(prev);
            if (StringUtils.isNotBlank(refinement)) {
                block += "\n## 修改意见\n" + refinement;
            }
        } else if (StringUtils.isNotBlank(refinement)) {
            block = "\n## 补充\n" + refinement;
        }
        return template.replace("{user_intent}", StringUtils.defaultString(userIntent))
                .replace("{refinement_block}", block);
    }

    private AssistCfg loadCfg() {
        String json = sysParamsService.getValue(PARAM_KEY, true);
        AssistCfg c = new AssistCfg();
        if (StringUtils.isBlank(json)) {
            log.warn("{} 未配置或 param_value 为空", PARAM_KEY);
            return c;
        }
        try {
            AssistCfg parsed = JsonUtils.parseObject(json, AssistCfg.class);
            if (parsed != null) {
                c = parsed;
            }
            JSONObject o = JSONUtil.parseObj(json);
            if (StringUtils.isBlank(c.getLlmModelId())) {
                c.setLlmModelId(StringUtils.trimToEmpty(o.getStr("llm_model_id")));
            }
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

    @Data
    private static final class AssistCfg {
        private boolean enabled = true;
        private String llmModelId = "";
        private String baseUrl = "";
        private String apiKey = "";
        private String modelName = "";
        private Double temperature;
        private Integer maxTokens;
    }
}
