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

    private static final String PROMPT_KEYWORD =
            """
            你是儿童对话「安全观察」设计助手。家长用口语描述担忧，需生成**家庭观察词**（关键词规则），不是聊天技能。

            watchType 固定为 KEYWORD。输出 JSON（不要 markdown）：
            {"name":"2~12字","description":"给家长看 20~60字","triggerHint":"当孩子说…时","riskDomain":"peer_relation等","pattern":"关键词或短语，可用|分隔多说法","riskLevel":2,"category":"须从该领域白名单选一项"}

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

            watchType 固定为 EVALUATOR。输出 JSON（不要 markdown）：
            {"name":"2~12字","description":"给家长看","triggerHint":"当孩子聊天涉及…","riskDomain":"一个领域code","instructions":"200~600字中文：触发场景、观察要点、输出约束（只判风险不写安慰）","allowedCategories":"JSON数组字符串如 [\"bullying\",\"other\"]"}

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
        ParentRiskWatchDraftVO vo = parseDraft(raw);
        if (vo == null) {
            throw new RenException("AI 返回格式异常，请重试");
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

    private ParentRiskWatchDraftVO parseDraft(String raw) {
        Matcher m = JSON_OBJECT.matcher(raw);
        if (!m.find()) {
            return null;
        }
        try {
            return JsonUtils.parseObject(m.group(), ParentRiskWatchDraftVO.class);
        } catch (Exception e) {
            return null;
        }
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
