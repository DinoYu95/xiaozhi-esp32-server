package xiaozhi.modules.growthportrait.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import xiaozhi.common.utils.JsonUtils;
import xiaozhi.modules.growthportrait.entity.GpTemplateNodeEntity;
import xiaozhi.modules.llm.dto.LlmOpenAiCallConfig;
import xiaozhi.modules.llm.service.LLMService;
import xiaozhi.modules.model.entity.ModelConfigEntity;
import xiaozhi.modules.model.service.ModelConfigService;
import xiaozhi.modules.sys.service.SysParamsService;

/**
 * 会话 transcript → LLM 漏斗（Sub → Signal），不依赖关键词。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GrowthPortraitClassifyService {

    private static final String PARAM_KEY = "server.growth_portrait_evidence_llm_config";
    private static final Pattern JSON_OBJECT = Pattern.compile("\\{.*\\}", Pattern.DOTALL);

    private static final String PROMPT_SUB = """
            你是儿童成长观察助手。阅读对话 transcript，判断孩子是否表现出下列「子能力」。
            只根据孩子实际言行判断，不要臆测。若无明确表现，返回空数组。

            ## 对话
            {transcript}

            ## 子能力（code | 名称）
            {sub_catalog}

            规则：
            - 最多 {max_obs} 项
            - confidence 0-100，仅 confidence>={min_conf} 才输出
            - reason 为 15 字内客观描述

            只输出 JSON，不要 markdown：
            {"observations":[{"subCode":"完整code","confidence":75,"reason":"..."}]}
            """;

    private static final String PROMPT_SIGNAL = """
            下列子能力在对话中已有表现，请为每个子能力选择最匹配的「观测信号」（每子能力最多 1 个）。

            ## 对话
            {transcript}

            ## 待选信号
            {signal_catalog}

            规则：
            - confidence 0-100，仅 confidence>={min_conf} 才输出
            - snippet 为 20 字内观察依据（客观描述孩子言行）

            只输出 JSON，不要 markdown：
            {"signals":[{"subCode":"...","signalCode":"完整signal code","confidence":80,"snippet":"..."}]}
            """;

    private final LLMService llmService;
    private final SysParamsService sysParamsService;
    private final ModelConfigService modelConfigService;

    public List<ClassifiedSignal> classify(String transcript, List<GpTemplateNodeEntity> allNodes) {
        if (StringUtils.isBlank(transcript)) {
            return List.of();
        }
        ClassifyCfg cfg = loadCfg();
        if (!cfg.isEnabled()) {
            log.debug("growth portrait LLM classify disabled");
            return List.of();
        }
        List<GpTemplateNodeEntity> hubs = filterType(allNodes, "hub");
        List<GpTemplateNodeEntity> subs = filterType(allNodes, "sub");
        List<GpTemplateNodeEntity> signals = filterType(allNodes, "signal");
        if (subs.isEmpty()) {
            return List.of();
        }
        Map<String, String> hubLabelByCode = hubs.stream()
                .collect(Collectors.toMap(GpTemplateNodeEntity::getCode, GpTemplateNodeEntity::getLabel, (a, b) -> a));
        String subCatalog = buildSubCatalog(subs, hubLabelByCode);
        String subPrompt = PROMPT_SUB
                .replace("{transcript}", truncate(transcript, 6000))
                .replace("{sub_catalog}", subCatalog)
                .replace("{max_obs}", String.valueOf(cfg.getMaxObservationsPerSession()))
                .replace("{min_conf}", String.valueOf(cfg.getMinConfidence()));
        String subRaw = callLlm(cfg, subPrompt);
        List<SubObservation> subHits = parseSubObservations(subRaw, cfg.getMinConfidence());
        if (subHits.isEmpty()) {
            return List.of();
        }
        Map<String, List<GpTemplateNodeEntity>> signalsBySub = signals.stream()
                .filter(s -> s.getParentCode() != null)
                .collect(Collectors.groupingBy(GpTemplateNodeEntity::getParentCode));
        String signalCatalog = buildSignalCatalog(subHits, subs, signalsBySub);
        if (StringUtils.isBlank(signalCatalog)) {
            return List.of();
        }
        String sigPrompt = PROMPT_SIGNAL
                .replace("{transcript}", truncate(transcript, 6000))
                .replace("{signal_catalog}", signalCatalog)
                .replace("{min_conf}", String.valueOf(cfg.getMinConfidence()));
        String sigRaw = callLlm(cfg, sigPrompt);
        return parseSignalHits(sigRaw, cfg.getMinConfidence());
    }

    private static List<GpTemplateNodeEntity> filterType(List<GpTemplateNodeEntity> nodes, String type) {
        return nodes.stream()
                .filter(n -> type.equals(n.getNodeType()))
                .sorted(java.util.Comparator.comparingInt(n -> n.getSortOrder() != null ? n.getSortOrder() : 0))
                .toList();
    }

    private static String buildSubCatalog(List<GpTemplateNodeEntity> subs, Map<String, String> hubLabelByCode) {
        Map<String, List<GpTemplateNodeEntity>> byHub = new LinkedHashMap<>();
        for (GpTemplateNodeEntity sub : subs) {
            String hubCode = sub.getParentCode() != null ? sub.getParentCode() : "_";
            byHub.computeIfAbsent(hubCode, k -> new ArrayList<>()).add(sub);
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<GpTemplateNodeEntity>> e : byHub.entrySet()) {
            String hubLabel = hubLabelByCode.getOrDefault(e.getKey(), "维度");
            sb.append("【").append(hubLabel).append("】\n");
            for (GpTemplateNodeEntity sub : e.getValue()) {
                sb.append("- ").append(sub.getCode()).append(" | ").append(sub.getLabel()).append('\n');
            }
        }
        return sb.toString();
    }

    private static String buildSignalCatalog(
            List<SubObservation> subHits,
            List<GpTemplateNodeEntity> subs,
            Map<String, List<GpTemplateNodeEntity>> signalsBySub) {
        Map<String, GpTemplateNodeEntity> subByCode = subs.stream()
                .collect(Collectors.toMap(GpTemplateNodeEntity::getCode, s -> s, (a, b) -> a));
        StringBuilder sb = new StringBuilder();
        for (SubObservation hit : subHits) {
            GpTemplateNodeEntity sub = subByCode.get(hit.subCode);
            if (sub == null) {
                continue;
            }
            List<GpTemplateNodeEntity> sigs = signalsBySub.getOrDefault(hit.subCode, List.of());
            if (sigs.isEmpty()) {
                continue;
            }
            sb.append("【").append(sub.getLabel()).append(" ").append(hit.subCode).append("】\n");
            for (GpTemplateNodeEntity sig : sigs) {
                sb.append("- ").append(sig.getCode()).append(" | ")
                        .append(StringUtils.defaultString(sig.getShortDesc(), sig.getLabel())).append('\n');
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private List<SubObservation> parseSubObservations(String raw, int minConf) {
        JSONObject root = parseJsonObject(raw);
        if (root == null) {
            return List.of();
        }
        JSONArray arr = root.getJSONArray("observations");
        if (arr == null || arr.isEmpty()) {
            return List.of();
        }
        List<SubObservation> out = new ArrayList<>();
        for (int i = 0; i < arr.size(); i++) {
            JSONObject o = arr.getJSONObject(i);
            if (o == null) {
                continue;
            }
            String code = StringUtils.trimToEmpty(o.getStr("subCode"));
            if (code.isEmpty()) {
                code = StringUtils.trimToEmpty(o.getStr("sub_code"));
            }
            Integer conf = o.getInt("confidence");
            if (conf == null || conf < minConf || code.isEmpty()) {
                continue;
            }
            SubObservation so = new SubObservation();
            so.subCode = code;
            so.confidence = conf;
            so.reason = truncate(StringUtils.defaultString(o.getStr("reason")), 64);
            out.add(so);
        }
        return out;
    }

    private List<ClassifiedSignal> parseSignalHits(String raw, int minConf) {
        JSONObject root = parseJsonObject(raw);
        if (root == null) {
            return List.of();
        }
        JSONArray arr = root.getJSONArray("signals");
        if (arr == null || arr.isEmpty()) {
            return List.of();
        }
        List<ClassifiedSignal> out = new ArrayList<>();
        for (int i = 0; i < arr.size(); i++) {
            JSONObject o = arr.getJSONObject(i);
            if (o == null) {
                continue;
            }
            String signalCode = StringUtils.trimToEmpty(o.getStr("signalCode"));
            if (signalCode.isEmpty()) {
                signalCode = StringUtils.trimToEmpty(o.getStr("signal_code"));
            }
            Integer conf = o.getInt("confidence");
            if (conf == null || conf < minConf || signalCode.isEmpty()) {
                continue;
            }
            ClassifiedSignal cs = new ClassifiedSignal();
            cs.signalCode = signalCode;
            cs.confidence = conf;
            cs.snippet = truncate(StringUtils.defaultString(o.getStr("snippet")), 120);
            out.add(cs);
        }
        return out;
    }

    private String callLlm(ClassifyCfg cfg, String prompt) {
        try {
            if (StringUtils.isNotBlank(cfg.getLlmModelId())) {
                if (!llmService.isAvailable(cfg.getLlmModelId())) {
                    log.warn("growth portrait classify LLM unavailable modelId={}", cfg.getLlmModelId());
                    return null;
                }
                return llmService.generateSummary("", prompt, cfg.getLlmModelId());
            }
            LlmOpenAiCallConfig inline = cfg.toInlineConfig();
            if (llmService.isInlineConfigAvailable(inline)) {
                return llmService.chatWithOpenAiConfig(prompt, inline);
            }
            if (llmService.isAvailable()) {
                return llmService.generateSummary("", prompt, null);
            }
            log.warn("growth portrait classify: no LLM configured ({})", PARAM_KEY);
            return null;
        } catch (Exception e) {
            log.warn("growth portrait classify LLM failed: {}", e.getMessage());
            return null;
        }
    }

    private ClassifyCfg loadCfg() {
        String json = sysParamsService.getValue(PARAM_KEY, true);
        ClassifyCfg c = new ClassifyCfg();
        if (StringUtils.isBlank(json)) {
            c.setEnabled(false);
            return c;
        }
        try {
            ClassifyCfg parsed = JsonUtils.parseObject(json, ClassifyCfg.class);
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
            if (o.getInt("min_confidence") != null) {
                c.setMinConfidence(o.getInt("min_confidence"));
            }
            if (o.getInt("max_observations_per_session") != null) {
                c.setMaxObservationsPerSession(o.getInt("max_observations_per_session"));
            }
            if (o.containsKey("enabled")) {
                c.setEnabled(o.getBool("enabled", true));
            }
        } catch (Exception e) {
            log.warn("parse {} failed: {}", PARAM_KEY, e.getMessage());
            c.setEnabled(false);
        }
        return c;
    }

    private static JSONObject parseJsonObject(String raw) {
        String json = extractJson(raw);
        if (json == null) {
            return null;
        }
        try {
            return JSONUtil.parseObj(json);
        } catch (Exception e) {
            log.debug("growth portrait classify parse json error: {}", e.getMessage());
            return null;
        }
    }

    private static String extractJson(String raw) {
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.contains("LLM服务不可用") || trimmed.contains("生成总结失败")) {
            return null;
        }
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

    @Data
    public static class ClassifiedSignal {
        private String signalCode;
        private int confidence;
        private String snippet;
    }

    @Data
    private static class SubObservation {
        private String subCode;
        private int confidence;
        private String reason;
    }

    @Data
    private static class ClassifyCfg {
        private boolean enabled = true;
        private String llmModelId = "";
        private String baseUrl = "";
        private String apiKey = "";
        private String modelName = "";
        private Double temperature = 0.0;
        private Integer maxTokens = 1500;
        private int minConfidence = 60;
        private int maxObservationsPerSession = 3;

        LlmOpenAiCallConfig toInlineConfig() {
            LlmOpenAiCallConfig c = new LlmOpenAiCallConfig();
            c.setBaseUrl(StringUtils.trimToEmpty(baseUrl));
            c.setApiKey(StringUtils.trimToEmpty(apiKey));
            c.setModelName(StringUtils.trimToEmpty(modelName));
            c.setTemperature(temperature != null ? temperature : 0.0);
            c.setMaxTokens(maxTokens != null ? maxTokens : 1500);
            return c;
        }
    }
}
