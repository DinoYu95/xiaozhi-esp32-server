package xiaozhi.modules.parent.service.impl;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
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
        if (!cfg.enabled) {
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
        if (StringUtils.isNotBlank(cfg.llmModelId)) {
            String modelId = resolveLlmModelId(cfg.llmModelId);
            raw = llmService.generateSummary("", prompt, modelId);
        } else if (StringUtils.isNotBlank(cfg.baseUrl) && StringUtils.isNotBlank(cfg.apiKey)) {
            LlmOpenAiCallConfig inline = new LlmOpenAiCallConfig();
            inline.setBaseUrl(cfg.baseUrl.trim());
            inline.setApiKey(cfg.apiKey.trim());
            inline.setModelName(StringUtils.trimToEmpty(cfg.modelName));
            inline.setTemperature(cfg.temperature);
            inline.setMaxTokens(cfg.maxTokens);
            raw = llmService.chatWithOpenAiConfig(prompt, inline);
        } else {
            if (!llmService.isAvailable()) {
                throw new RenException("AI 服务不可用，请配置 parent_risk_watch_assist_config");
            }
            raw = llmService.generateSummary("", prompt, null);
        }
        if (StringUtils.isBlank(raw) || raw.contains("生成总结失败")) {
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
        AssistCfg c = new AssistCfg();
        String json = sysParamsService.getValue(PARAM_KEY, true);
        if (StringUtils.isBlank(json)) {
            return c;
        }
        try {
            AssistCfg p = JsonUtils.parseObject(json, AssistCfg.class);
            if (p != null) {
                c = p;
            }
            JSONObject o = JSONUtil.parseObj(json);
            if (StringUtils.isBlank(c.baseUrl)) {
                c.baseUrl = o.getStr("base_url", "");
            }
            if (StringUtils.isBlank(c.apiKey)) {
                c.apiKey = o.getStr("api_key", "");
            }
            if (StringUtils.isBlank(c.modelName)) {
                c.modelName = o.getStr("model_name", "");
            }
        } catch (Exception ignored) {
        }
        return c;
    }

    private String resolveLlmModelId(String id) {
        ModelConfigEntity model = modelConfigService.getModelByIdFromCache(id);
        if (model == null || model.getIsEnabled() == null || model.getIsEnabled() != 1) {
            throw new RenException("llmModelId 无效或未启用: " + id);
        }
        return id;
    }

    private static class AssistCfg {
        boolean enabled = true;
        String llmModelId = "";
        String baseUrl = "";
        String apiKey = "";
        String modelName = "";
        Double temperature;
        Integer maxTokens;
    }
}
