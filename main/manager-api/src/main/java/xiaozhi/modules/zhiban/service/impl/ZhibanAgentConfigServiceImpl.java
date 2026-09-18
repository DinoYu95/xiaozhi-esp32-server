package xiaozhi.modules.zhiban.service.impl;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import cn.hutool.json.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.utils.JsonUtils;
import xiaozhi.modules.model.entity.ModelConfigEntity;
import xiaozhi.modules.model.service.ModelConfigService;
import xiaozhi.modules.sys.service.SysParamsService;
import xiaozhi.modules.zhiban.dto.ZhibanAgentConfigDTO;
import xiaozhi.modules.zhiban.dto.ZhibanAgentLlmProfileDTO;
import xiaozhi.modules.zhiban.service.ZhibanAgentConfigService;
import xiaozhi.modules.zhiban.vo.ZhibanAgentLlmProfileRuntimeVO;
import xiaozhi.modules.zhiban.vo.ZhibanAgentRuntimeVO;

@Slf4j
@Service
@RequiredArgsConstructor
public class ZhibanAgentConfigServiceImpl implements ZhibanAgentConfigService {

    public static final String PARAM_KEY = "server.zhiban_agent_config";

    private static final String[] DEFAULT_PROFILE_KEYS = {
        "router", "chat", "vision", "extract", "parentTools", "childRiskJudge"
    };

    private final SysParamsService sysParamsService;
    private final ModelConfigService modelConfigService;

    @Override
    public ZhibanAgentConfigDTO getAdminConfig() {
        return loadConfig();
    }

    @Override
    public void saveAdminConfig(ZhibanAgentConfigDTO dto) {
        if (dto == null) {
            throw new RenException("配置不能为空");
        }
        if (dto.getVersion() == null) {
            dto.setVersion(1);
        }
        ensureDefaultProfiles(dto);
        sysParamsService.updateValueByCode(PARAM_KEY, JsonUtils.toJsonString(dto));
    }

    @Override
    public ZhibanAgentRuntimeVO getRuntimeConfig() {
        ZhibanAgentConfigDTO cfg = loadConfig();
        ZhibanAgentRuntimeVO runtime = JsonUtils.parseObject(JsonUtils.toJsonString(cfg), ZhibanAgentRuntimeVO.class);
        if (runtime == null) {
            runtime = new ZhibanAgentRuntimeVO();
        }
        Map<String, ZhibanAgentLlmProfileRuntimeVO> resolved = new LinkedHashMap<>();
        if (cfg.getLlmProfiles() != null) {
            cfg.getLlmProfiles().forEach((k, v) -> resolved.put(k, resolveProfile(v)));
        }
        runtime.setResolvedLlmProfiles(resolved);
        runtime.setResolvedEmbedding(resolveProfile(cfg.getEmbedding()));
        return runtime;
    }

    private ZhibanAgentConfigDTO loadConfig() {
        String json = sysParamsService.getValue(PARAM_KEY, true);
        ZhibanAgentConfigDTO cfg;
        if (StringUtils.isBlank(json)) {
            cfg = defaultConfig();
        } else {
            cfg = JsonUtils.parseObject(json, ZhibanAgentConfigDTO.class);
            if (cfg == null) {
                cfg = defaultConfig();
            }
        }
        ensureDefaultProfiles(cfg);
        if (cfg.getIntegration() == null) {
            cfg.setIntegration(new ZhibanAgentConfigDTO.ZhibanAgentIntegrationDTO());
        }
        if (cfg.getMemory() == null) {
            cfg.setMemory(new ZhibanAgentConfigDTO.ZhibanAgentMemoryDTO());
        }
        if (cfg.getFeatures() == null) {
            cfg.setFeatures(new ZhibanAgentConfigDTO.ZhibanAgentFeaturesDTO());
        }
        if (cfg.getEmbedding() == null) {
            cfg.setEmbedding(new ZhibanAgentLlmProfileDTO());
        }
        return cfg;
    }

    private static ZhibanAgentConfigDTO defaultConfig() {
        ZhibanAgentConfigDTO cfg = new ZhibanAgentConfigDTO();
        Map<String, ZhibanAgentLlmProfileDTO> profiles = new LinkedHashMap<>();
        profiles.put("router", profile("", 0.0, 256));
        profiles.put("chat", profile("", 0.45, null));
        profiles.put("vision", profile("", 0.35, null));
        profiles.put("extract", profile("", 0.0, null));
        profiles.put("parentTools", profile("", 0.35, null));
        profiles.put("childRiskJudge", profile("", 0.0, null));
        cfg.setLlmProfiles(profiles);
        cfg.setEmbedding(new ZhibanAgentLlmProfileDTO());
        cfg.setIntegration(new ZhibanAgentConfigDTO.ZhibanAgentIntegrationDTO());
        cfg.setMemory(new ZhibanAgentConfigDTO.ZhibanAgentMemoryDTO());
        cfg.setFeatures(new ZhibanAgentConfigDTO.ZhibanAgentFeaturesDTO());
        return cfg;
    }

    private static ZhibanAgentLlmProfileDTO profile(String modelId, double temp, Integer maxTokens) {
        ZhibanAgentLlmProfileDTO p = new ZhibanAgentLlmProfileDTO();
        p.setLlmModelId(modelId);
        p.setTemperature(temp);
        p.setMaxTokens(maxTokens);
        return p;
    }

    private static void ensureDefaultProfiles(ZhibanAgentConfigDTO cfg) {
        if (cfg.getLlmProfiles() == null) {
            cfg.setLlmProfiles(new LinkedHashMap<>());
        }
        ZhibanAgentConfigDTO defaults = defaultConfig();
        for (String key : DEFAULT_PROFILE_KEYS) {
            cfg.getLlmProfiles().putIfAbsent(key, defaults.getLlmProfiles().get(key));
        }
    }

    private ZhibanAgentLlmProfileRuntimeVO resolveProfile(ZhibanAgentLlmProfileDTO profile) {
        if (profile == null || StringUtils.isBlank(profile.getLlmModelId())) {
            return null;
        }
        ModelConfigEntity model = modelConfigService.getModelByIdFromCache(profile.getLlmModelId().trim());
        if (model == null || model.getConfigJson() == null) {
            log.warn("zhiban profile llmModelId={} 未找到或未启用", profile.getLlmModelId());
            return null;
        }
        JSONObject json = model.getConfigJson();
        String baseUrl = StringUtils.trimToEmpty(json.getStr("base_url"));
        String apiKey = StringUtils.trimToEmpty(json.getStr("api_key"));
        String modelName = StringUtils.trimToEmpty(json.getStr("model_name"));
        if (StringUtils.isBlank(baseUrl) || StringUtils.isBlank(apiKey)) {
            log.warn("zhiban profile llmModelId={} 缺少 base_url 或 api_key", profile.getLlmModelId());
            return null;
        }
        ZhibanAgentLlmProfileRuntimeVO vo = new ZhibanAgentLlmProfileRuntimeVO();
        vo.setLlmModelId(profile.getLlmModelId().trim());
        vo.setApiBase(baseUrl);
        vo.setApiKey(apiKey);
        vo.setModel(StringUtils.defaultIfBlank(modelName, "gpt-3.5-turbo"));
        if (profile.getTemperature() != null) {
            vo.setTemperature(profile.getTemperature());
        } else if (json.getDouble("temperature") != null) {
            vo.setTemperature(json.getDouble("temperature"));
        }
        if (profile.getMaxTokens() != null) {
            vo.setMaxTokens(profile.getMaxTokens());
        } else if (json.getInt("max_tokens") != null) {
            vo.setMaxTokens(json.getInt("max_tokens"));
        }
        return vo;
    }
}
