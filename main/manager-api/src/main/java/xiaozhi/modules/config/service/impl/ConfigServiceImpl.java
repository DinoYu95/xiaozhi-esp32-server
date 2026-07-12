package xiaozhi.modules.config.service.impl;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import xiaozhi.common.constant.Constant;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.redis.RedisKeys;
import xiaozhi.common.redis.RedisUtils;
import xiaozhi.common.utils.ConvertUtils;
import xiaozhi.common.utils.JsonUtils;
import xiaozhi.modules.agent.dao.AgentSkillMappingDao;
import xiaozhi.modules.agent.dao.AgentVoicePrintDao;
import xiaozhi.modules.agent.entity.AgentContextProviderEntity;
import xiaozhi.modules.agent.entity.AgentEntity;
import xiaozhi.modules.agent.entity.AgentPluginMapping;
import xiaozhi.modules.agent.entity.AgentTemplateEntity;
import xiaozhi.modules.agent.entity.AgentSkillMappingEntity;
import xiaozhi.modules.agent.entity.AgentVoicePrintEntity;
import xiaozhi.modules.agent.service.AgentContextProviderService;
import xiaozhi.modules.agent.service.AgentMcpAccessPointService;
import xiaozhi.modules.agent.service.AgentPluginMappingService;
import xiaozhi.modules.agent.service.AgentService;
import xiaozhi.modules.agent.service.AgentSkillService;
import xiaozhi.modules.agent.service.AgentTemplateService;
import xiaozhi.modules.agent.vo.AgentVoicePrintVO;
import xiaozhi.modules.config.service.ConfigService;
import xiaozhi.modules.device.entity.DeviceEntity;
import xiaozhi.modules.device.service.DeviceService;
import xiaozhi.modules.parent.dao.DeviceChildDao;
import xiaozhi.modules.parent.entity.DeviceChildEntity;
import xiaozhi.modules.parent.consent.service.ParentConsentService;
import xiaozhi.modules.parent.service.ParentDeviceRuleService;
import xiaozhi.modules.model.entity.ModelConfigEntity;
import xiaozhi.modules.model.service.ModelConfigService;
import xiaozhi.modules.sys.dto.SysParamsDTO;
import xiaozhi.modules.sys.service.SysParamsService;
import xiaozhi.modules.timbre.service.TimbreService;
import xiaozhi.modules.timbre.vo.TimbreDetailsVO;
import xiaozhi.modules.voiceclone.entity.VoiceCloneEntity;
import xiaozhi.modules.voiceclone.service.VoiceCloneService;

@Service
@AllArgsConstructor
@Slf4j
public class ConfigServiceImpl implements ConfigService {
    private final SysParamsService sysParamsService;
    private final DeviceService deviceService;
    private final ModelConfigService modelConfigService;
    private final AgentService agentService;
    private final AgentTemplateService agentTemplateService;
    private final RedisUtils redisUtils;
    private final TimbreService timbreService;
    private final AgentPluginMappingService agentPluginMappingService;
    private final AgentMcpAccessPointService agentMcpAccessPointService;
    private final AgentContextProviderService agentContextProviderService;
    private final VoiceCloneService cloneVoiceService;
    private final AgentVoicePrintDao agentVoicePrintDao;
    private final AgentSkillMappingDao agentSkillMappingDao;
    private final AgentSkillService agentSkillService;
    private final DeviceChildDao deviceChildDao;
    private final ParentDeviceRuleService parentDeviceRuleService;
    private final ParentConsentService parentConsentService;

    @Override
    public Object getConfig(Boolean isCache) {
        if (isCache) {
            // 先从Redis获取配置
            Object cachedConfig = redisUtils.get(RedisKeys.getServerConfigKey());
            if (cachedConfig != null) {
                return cachedConfig;
            }
        }

        // 构建配置信息
        Map<String, Object> result = new HashMap<>();
        buildConfig(result);

        // 查询默认智能体
        AgentTemplateEntity agent = agentTemplateService.getDefaultTemplate();
        if (agent == null) {
            throw new RenException(ErrorCode.AGENT_TEMPLATE_NOT_FOUND);
        }

        // 构建模块配置
        buildModuleConfig(
                null,
                null,
                null,
                null,
                null,
                null,
                agent.getVadModelId(),
                agent.getAsrModelId(),
                null,
                null,
                null,
                null,
                null,
                null,
                result,
                isCache);

        // 将配置存入Redis
        redisUtils.set(RedisKeys.getServerConfigKey(), result);

        return result;
    }

    @Override
    public Map<String, Object> getAgentModels(String macAddress, Map<String, String> selectedModule) {
        // 检查是否为管理控制台请求
        String redisKey = RedisKeys.getTmpRegisterMacKey(macAddress);
        Object isAdminRequest = redisUtils.get(redisKey);
        
        if (isAdminRequest != null && "true".equals(isAdminRequest)) {
            // 管理控制台请求，返回getConfig的结果
            redisUtils.delete(redisKey); // 使用后清理
            return (Map<String, Object>) getConfig(true);
        }
        // 根据MAC地址查找设备
        DeviceEntity device = deviceService.getDeviceByMacAddress(macAddress);
        if (device == null) {
            // 如果设备，去redis里看看有没有需要连接的设备
            String cachedCode = deviceService.geCodeByDeviceId(macAddress);
            if (StringUtils.isNotBlank(cachedCode)) {
                throw new RenException(ErrorCode.OTA_DEVICE_NEED_BIND, cachedCode);
            }
            throw new RenException(ErrorCode.OTA_DEVICE_NOT_FOUND);
        }

        // 获取智能体信息
        AgentEntity agent = agentService.getAgentById(device.getAgentId());
        if (agent == null) {
            throw new RenException(ErrorCode.AGENT_NOT_FOUND);
        }
        // 获取音色信息（未同意协议时仍需 TTS 播报提示）
        String voice = null;
        String referenceAudio = null;
        String referenceText = null;
        TimbreDetailsVO timbre = timbreService.get(agent.getTtsVoiceId());
        if (timbre != null) {
            voice = timbre.getTtsVoice();
            referenceAudio = timbre.getReferenceAudio();
            referenceText = timbre.getReferenceText();
        } else {
            VoiceCloneEntity voice_print = cloneVoiceService.selectById(agent.getTtsVoiceId());
            if (voice_print != null) {
                voice = voice_print.getVoiceId();
            }
        }

        if (!parentConsentService.isDeviceConsentOk(macAddress, device.getMacAddress())) {
            log.info(
                    "设备隐私协议未通过，返回最小配置: requestMac={}, deviceMac={}",
                    macAddress,
                    device.getMacAddress());
            Map<String, Object> result = new HashMap<>();
            buildModuleConfig(
                    null,
                    null,
                    null,
                    voice,
                    referenceAudio,
                    referenceText,
                    null,
                    null,
                    null,
                    null,
                    agent.getTtsModelId(),
                    null,
                    null,
                    null,
                    result,
                    true);
            result.put("need_consent", true);
            result.put("consent_blocked_prompt", parentConsentService.getDeviceBlockedPrompt());
            return result;
        }

        // 构建返回数据
        Map<String, Object> result = new HashMap<>();
        // 获取单台设备每天最多输出字数
        String deviceMaxOutputSize = sysParamsService.getValue("device_max_output_size", true);
        result.put("device_max_output_size", deviceMaxOutputSize);

        // 获取聊天记录配置（与 memory model 解耦：无记忆时仍可开启聊天上报，供家长端拉取孩子对话）
        Integer chatHistoryConf = agent.getChatHistoryConf();
        if (chatHistoryConf == null) {
            // 未显式配置时：有记忆/无记忆均默认开启文本+语音（无脑开启，供家长端拉取；用户可在智控台改为 0 关闭）
            chatHistoryConf = Constant.ChatHistoryConfEnum.RECORD_TEXT_AUDIO.getCode();
        }
        result.put("chat_history_conf", chatHistoryConf);
        // 如果客户端已实例化模型，则不返回
        String alreadySelectedVadModelId = selectedModule.get("VAD");
        if (alreadySelectedVadModelId != null && alreadySelectedVadModelId.equals(agent.getVadModelId())) {
            agent.setVadModelId(null);
        }
        String alreadySelectedAsrModelId = selectedModule.get("ASR");
        if (alreadySelectedAsrModelId != null && alreadySelectedAsrModelId.equals(agent.getAsrModelId())) {
            agent.setAsrModelId(null);
        }

        // 添加函数调用参数信息
        if (!Objects.equals(agent.getIntentModelId(), "Intent_nointent")) {
            String agentId = agent.getId();
            List<AgentPluginMapping> pluginMappings = agentPluginMappingService.agentPluginParamsByAgentId(agentId);
            if (pluginMappings != null && !pluginMappings.isEmpty()) {
                Map<String, Object> pluginParams = new HashMap<>();
                for (AgentPluginMapping pluginMapping : pluginMappings) {
                    pluginParams.put(pluginMapping.getProviderCode(), pluginMapping.getParamInfo());
                }
                result.put("plugins", pluginParams);
            }
        }
        // 获取mcp接入点地址
        String mcpEndpoint = agentMcpAccessPointService.getAgentMcpAccessAddress(agent.getId());
        if (StringUtils.isNotBlank(mcpEndpoint) && mcpEndpoint.startsWith("ws")) {
            mcpEndpoint = mcpEndpoint.replace("/mcp/", "/call/");
            result.put("mcp_endpoint", mcpEndpoint);
        }
        
        // 获取上下文源配置
        AgentContextProviderEntity contextProviderEntity = agentContextProviderService.getByAgentId(agent.getId());
        if (contextProviderEntity != null && contextProviderEntity.getContextProviders() != null && !contextProviderEntity.getContextProviders().isEmpty()) {
            result.put("context_providers", contextProviderEntity.getContextProviders());
        }

        // 获取声纹信息（按设备过滤：本设备主孩子 + 后台声纹）
        buildVoiceprintConfig(agent.getId(), result, device.getId());

        // 多角色与智伴：下发主孩子信息（供 xiaozhi 算 estimated_age、is_owner_child）及说话人类型→技能映射
        DeviceChildEntity deviceChild = deviceChildDao.selectOne(
                new LambdaQueryWrapper<DeviceChildEntity>().eq(DeviceChildEntity::getDeviceId, device.getId()));
        if (deviceChild != null) {
            result.put("owner_child_id", deviceChild.getId());
            result.put("owner_child_birthday", deviceChild.getBirthday() != null ? deviceChild.getBirthday().toString() : null);
            AgentVoicePrintEntity ownerVoicePrint = agentVoicePrintDao.selectOne(
                    new LambdaQueryWrapper<AgentVoicePrintEntity>()
                            .eq(AgentVoicePrintEntity::getAgentId, agent.getId())
                            .eq(AgentVoicePrintEntity::getChildId, deviceChild.getId()));
            if (ownerVoicePrint != null) {
                result.put("owner_child_voice_print_id", ownerVoicePrint.getId());
            }
        }
        // 一说话人对应多技能：speaker_type -> List<skill_id>，由意图决定走哪个 skill
        List<AgentSkillMappingEntity> skillMappings = agentSkillMappingDao.selectList(
                new LambdaQueryWrapper<AgentSkillMappingEntity>().eq(AgentSkillMappingEntity::getAgentId, agent.getId()));
        if (skillMappings != null && !skillMappings.isEmpty()) {
            Map<String, List<String>> skillMapping = new HashMap<>();
            for (AgentSkillMappingEntity m : skillMappings) {
                skillMapping.computeIfAbsent(m.getSpeakerType(), k -> new ArrayList<>()).add(m.getSkillId());
            }
            result.put("skill_mapping", skillMapping);
        }
        String defaultFallbackSkillId = agentSkillService.getDefaultFallbackSkillId();
        if (StringUtils.isNotBlank(defaultFallbackSkillId)) {
            result.put("default_fallback_skill_id", defaultFallbackSkillId);
        }

        // 构建模块配置
        String assistantName = resolveAssistantName(device, agent);
        buildModuleConfig(
                assistantName,
                agent.getSystemPrompt(),
                agent.getSummaryMemory(),
                voice,
                referenceAudio,
                referenceText,
                agent.getVadModelId(),
                agent.getAsrModelId(),
                agent.getLlmModelId(),
                agent.getVllmModelId(),
                agent.getTtsModelId(),
                agent.getMemModelId(),
                agent.getIntentModelId(),
                null,
                result,
                true);

        // 家长为该设备设置的规则（供 xiaozhi-server 注入到 prompt）
        // 兼容 device_id 格式：ai_device.id 可能为 UUID，parent_device_rule 可能存 mac/b6_c8_35/B6:C8:35 等；依次尝试
        List<String> parentRules = parentDeviceRuleService.getRuleTextsByDeviceId(device.getId());
        String matchedBy = null;
        if (parentRules != null && !parentRules.isEmpty()) {
            matchedBy = "device.id";
        } else if (StringUtils.isNotBlank(device.getMacAddress()) && !device.getMacAddress().equals(device.getId())) {
            parentRules = parentDeviceRuleService.getRuleTextsByDeviceId(device.getMacAddress());
            if (parentRules != null && !parentRules.isEmpty()) matchedBy = "mac_address";
        }
        if ((parentRules == null || parentRules.isEmpty()) && StringUtils.isNotBlank(device.getMacAddress())) {
            String norm = device.getMacAddress().replace(":", "_").toLowerCase();
            if (matchedBy == null && !norm.equals(device.getId()) && !norm.equals(device.getMacAddress())) {
                parentRules = parentDeviceRuleService.getRuleTextsByDeviceId(norm);
                if (parentRules != null && !parentRules.isEmpty()) matchedBy = "mac_norm";
            }
        }
        if ((parentRules == null || parentRules.isEmpty()) && StringUtils.isNotBlank(device.getMacAddress())) {
            String macUpper = device.getMacAddress().toUpperCase();
            if (!macUpper.equals(device.getMacAddress())) {
                parentRules = parentDeviceRuleService.getRuleTextsByDeviceId(macUpper);
                if (parentRules != null && !parentRules.isEmpty()) matchedBy = "mac_upper";
            }
        }
        if ((parentRules == null || parentRules.isEmpty()) && StringUtils.isNotBlank(device.getMacAddress())) {
            String macLower = device.getMacAddress().toLowerCase();
            if (!macLower.equals(device.getMacAddress()) && !macLower.equals(device.getId())) {
                parentRules = parentDeviceRuleService.getRuleTextsByDeviceId(macLower);
                if (parentRules != null && !parentRules.isEmpty()) matchedBy = "mac_lower";
            }
        }
        if (parentRules != null && !parentRules.isEmpty()) {
            result.put("parent_rules", parentRules);
            log.info("getAgentModels 下发 parent_rules {} 条，匹配方式: device.id={} mac={}", parentRules.size(),
                    device.getId(), matchedBy);
        } else {
            log.debug("getAgentModels 未查到 parent_rules，device.id={} mac_address={}", device.getId(),
                    device.getMacAddress());
        }

        String companionPrompt = buildCompanionGrowthPrompt(deviceChild);
        if (StringUtils.isNotBlank(companionPrompt)) {
            result.put("companion_growth_prompt", companionPrompt);
        }

        // 智伴 Agent：下发助手名称（优先家长自定义 device.alias，否则 agent.agent_name）
        if (StringUtils.isNotBlank(assistantName)) {
            result.put("assistant_name", assistantName);
        }

        return result;
    }

    /** 设备对话自称：优先家长端设置的 alias，否则智能体名称 */
    private static String resolveAssistantName(DeviceEntity device, AgentEntity agent) {
        if (device != null && StringUtils.isNotBlank(device.getAlias())) {
            return device.getAlias().trim();
        }
        if (agent != null && StringUtils.isNotBlank(agent.getAgentName())) {
            return agent.getAgentName().trim();
        }
        return null;
    }

    /**
     * 读取智控台「成长陪伴」模板，按 {@link DeviceChildEntity} 替换占位符，下发为 companion_growth_prompt。
     * 无模板、模板为 null 或空白则不返回；无主孩子时占位符置空，仍可下发纯静态模板。
     */
    private String buildCompanionGrowthPrompt(DeviceChildEntity deviceChild) {
        String template = sysParamsService.getValue(Constant.SERVER_AGENT_COMPANION_GROWTH_PROMPT_TEMPLATE, true);
        if (StringUtils.isBlank(template) || "null".equalsIgnoreCase(template.trim())) {
            return null;
        }
        LocalDate today = LocalDate.now();
        String childName = "";
        String childAgeYears = "";
        String childBirthday = "";
        String ageStage = "";
        String hobbies = "";
        String favoriteTopics = "";
        String favoriteStories = "";
        String personalityNote = "";
        String school = "";
        if (deviceChild != null) {
            childName = nz(deviceChild.getName());
            if (deviceChild.getBirthday() != null) {
                childBirthday = deviceChild.getBirthday().toString();
                childAgeYears = String.valueOf(Period.between(deviceChild.getBirthday(), today).getYears());
            }
            ageStage = nz(deviceChild.getAgeStage());
            hobbies = nz(deviceChild.getHobbies());
            favoriteTopics = nz(deviceChild.getFavoriteTopics());
            favoriteStories = nz(deviceChild.getFavoriteStories());
            personalityNote = nz(deviceChild.getPersonalityNote());
            school = nz(deviceChild.getSchool());
        }
        String out = template;
        out = out.replace("{child_name}", childName);
        out = out.replace("{child_age_years}", childAgeYears);
        out = out.replace("{child_birthday}", childBirthday);
        out = out.replace("{age_stage}", ageStage);
        out = out.replace("{hobbies}", hobbies);
        out = out.replace("{favorite_topics}", favoriteTopics);
        out = out.replace("{favorite_stories}", favoriteStories);
        out = out.replace("{personality_note}", personalityNote);
        out = out.replace("{school}", school);
        out = out.trim();
        return StringUtils.isBlank(out) ? null : out;
    }

    private static String nz(String s) {
        return s != null ? s : "";
    }

    /**
     * 构建配置信息
     * 
     * @param config 系统参数列表
     * @return 配置信息
     */
    private Object buildConfig(Map<String, Object> config) {

        // 查询所有系统参数
        List<SysParamsDTO> paramsList = sysParamsService.list(new HashMap<>());

        for (SysParamsDTO param : paramsList) {
            String[] keys = param.getParamCode().split("\\.");
            Map<String, Object> current = config;

            // 遍历除最后一个key之外的所有key
            for (int i = 0; i < keys.length - 1; i++) {
                String key = keys[i];
                if (!current.containsKey(key)) {
                    current.put(key, new HashMap<String, Object>());
                }
                current = (Map<String, Object>) current.get(key);
            }

            // 处理最后一个key
            String lastKey = keys[keys.length - 1];
            String value = param.getParamValue();

            // 根据valueType转换值
            switch (param.getValueType().toLowerCase()) {
                case "number":
                    try {
                        double doubleValue = Double.parseDouble(value);
                        // 如果数值是整数形式，则转换为Integer
                        if (doubleValue == (int) doubleValue) {
                            current.put(lastKey, (int) doubleValue);
                        } else {
                            current.put(lastKey, doubleValue);
                        }
                    } catch (NumberFormatException e) {
                        current.put(lastKey, value);
                    }
                    break;
                case "boolean":
                    current.put(lastKey, Boolean.parseBoolean(value));
                    break;
                case "array":
                    // 将分号分隔的字符串转换为数字数组
                    List<String> list = new ArrayList<>();
                    for (String num : value.split(";")) {
                        if (StringUtils.isNotBlank(num)) {
                            list.add(num.trim());
                        }
                    }
                    current.put(lastKey, list);
                    break;
                case "json":
                    try {
                        current.put(lastKey, JsonUtils.parseObject(value, Object.class));
                    } catch (Exception e) {
                        current.put(lastKey, value);
                    }
                    break;
                default:
                    current.put(lastKey, value);
            }
        }

        return config;
    }

    /**
     * 构建声纹配置（设备拉取时仅返回：后台声纹 + 本设备主孩子声纹）
     *
     * @param agentId  智能体ID
     * @param result   结果Map
     * @param deviceId 设备ID，可为 null（如 getConfig 时无设备则不过滤）
     */
    private void buildVoiceprintConfig(String agentId, Map<String, Object> result, String deviceId) {
        try {
            // 获取声纹接口地址
            String voiceprintUrl = sysParamsService.getValue(Constant.SERVER_VOICE_PRINT, true);
            if (StringUtils.isBlank(voiceprintUrl) || "null".equals(voiceprintUrl)) {
                return;
            }

            // 获取智能体关联的声纹信息；有 deviceId 时仅返回后台声纹 + 本设备主孩子声纹
            List<AgentVoicePrintVO> voiceprints = getVoiceprintsByAgentId(agentId, deviceId);
            if (voiceprints == null || voiceprints.isEmpty()) {
                return;
            }

            // 构建speakers列表
            List<String> speakers = new ArrayList<>();
            for (AgentVoicePrintVO voiceprint : voiceprints) {
                String speakerStr = String.format("%s,%s,%s",
                        voiceprint.getId(),
                        voiceprint.getSourceName(),
                        voiceprint.getIntroduce() != null ? voiceprint.getIntroduce() : "");
                speakers.add(speakerStr);
            }

            // 构建声纹配置
            Map<String, Object> voiceprintConfig = new HashMap<>();
            voiceprintConfig.put("url", voiceprintUrl);
            voiceprintConfig.put("speakers", speakers);

            // 获取声纹识别相似度阈值，默认0.4
            String thresholdStr = sysParamsService.getValue("server.voiceprint_similarity_threshold", true);
            if (StringUtils.isNotBlank(thresholdStr) && !"null".equals(thresholdStr)) {
                try {
                    double threshold = Double.parseDouble(thresholdStr);
                    voiceprintConfig.put("similarity_threshold", threshold);
                } catch (NumberFormatException e) {
                    // 如果解析失败，使用默认值0.4
                    voiceprintConfig.put("similarity_threshold", 0.4);
                }
            } else {
                voiceprintConfig.put("similarity_threshold", 0.4);
            }

            result.put("voiceprint", voiceprintConfig);
        } catch (Exception e) {
            // 声纹配置获取失败时不影响其他功能
            System.err.println("获取声纹配置失败: " + e.getMessage());
        }
    }

    /**
     * 获取智能体关联的声纹信息
     *
     * @param agentId  智能体ID
     * @param deviceId 设备ID；非空时仅返回 child_id IS NULL（后台声纹）或 child 属于该设备的声纹
     * @return 声纹信息列表
     */
    private List<AgentVoicePrintVO> getVoiceprintsByAgentId(String agentId, String deviceId) {
        LambdaQueryWrapper<AgentVoicePrintEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(AgentVoicePrintEntity::getAgentId, agentId);
        if (StringUtils.isNotBlank(deviceId)) {
            DeviceChildEntity child = deviceChildDao.selectOne(
                    new LambdaQueryWrapper<DeviceChildEntity>().eq(DeviceChildEntity::getDeviceId, deviceId));
            if (child != null) {
                queryWrapper.and(w -> w.isNull(AgentVoicePrintEntity::getChildId)
                        .or().eq(AgentVoicePrintEntity::getChildId, child.getId()));
            } else {
                queryWrapper.isNull(AgentVoicePrintEntity::getChildId);
            }
        }
        queryWrapper.orderByAsc(AgentVoicePrintEntity::getCreateDate);
        List<AgentVoicePrintEntity> entities = agentVoicePrintDao.selectList(queryWrapper);
        return ConvertUtils.sourceToTarget(entities, AgentVoicePrintVO.class);
    }

    /**
     * 构建模块配置
     * 
     * @param prompt         提示词
     * @param voice          音色
     * @param referenceAudio 参考音频路径
     * @param referenceText  参考文本
     * @param vadModelId     VAD模型ID
     * @param asrModelId     ASR模型ID
     * @param llmModelId     LLM模型ID
     * @param ttsModelId     TTS模型ID
     * @param memModelId     记忆模型ID
     * @param intentModelId  意图模型ID
     * @param result         结果Map
     */
    private void buildModuleConfig(
            String assistantName,
            String prompt,
            String summaryMemory,
            String voice,
            String referenceAudio,
            String referenceText,
            String vadModelId,
            String asrModelId,
            String llmModelId,
            String vllmModelId,
            String ttsModelId,
            String memModelId,
            String intentModelId,
            String ragModelId,
            Map<String, Object> result,
            boolean isCache) {
        Map<String, String> selectedModule = new HashMap<>();

        String[] modelTypes = { "VAD", "ASR", "TTS", "Memory", "Intent", "LLM", "VLLM", "RAG" };
        String[] modelIds = { vadModelId, asrModelId, ttsModelId, memModelId, intentModelId, llmModelId, vllmModelId,
                ragModelId };
        String intentLLMModelId = null;
        String memLocalShortLLMModelId = null;

        for (int i = 0; i < modelIds.length; i++) {
            if (modelIds[i] == null) {
                continue;
            }
            // 关键：第三个参数传false，确保获取原始密钥
            ModelConfigEntity model = modelConfigService.getModelByIdFromCache(modelIds[i]);
            if (model == null) {
                continue;
            }
            Map<String, Object> typeConfig = new HashMap<>();
            if (model.getConfigJson() != null) {
                typeConfig.put(model.getId(), model.getConfigJson());
                // 如果是TTS类型，添加private_voice属性
                if ("TTS".equals(modelTypes[i])) {
                    if (voice != null)
                        ((Map<String, Object>) model.getConfigJson()).put("private_voice", voice);
                    if (referenceAudio != null)
                        ((Map<String, Object>) model.getConfigJson()).put("ref_audio", referenceAudio);
                    if (referenceText != null)
                        ((Map<String, Object>) model.getConfigJson()).put("ref_text", referenceText);

                    // 火山引擎声音克隆需要替换resource_id
                    Map<String, Object> map = (Map<String, Object>) model.getConfigJson();
                    if (Constant.VOICE_CLONE_HUOSHAN_DOUBLE_STREAM.equals(map.get("type"))) {
                        // 如果voice是”S_“开头的，使用seed-icl-1.0
                        if (voice != null && voice.startsWith("S_")) {
                            map.put("resource_id", "seed-icl-1.0");
                        }
                    }
                }
                // 如果是Intent类型，且type=intent_llm，则给他添加附加模型
                if ("Intent".equals(modelTypes[i])) {
                    Map<String, Object> map = (Map<String, Object>) model.getConfigJson();
                    if ("intent_llm".equals(map.get("type"))) {
                        intentLLMModelId = (String) map.get("llm");
                        if (StringUtils.isNotBlank(intentLLMModelId) && intentLLMModelId.equals(llmModelId)) {
                            intentLLMModelId = null;
                        }
                    }
                    if (map.get("functions") != null) {
                        String functionStr = (String) map.get("functions");
                        if (StringUtils.isNotBlank(functionStr)) {
                            String[] functions = functionStr.split("\\;");
                            map.put("functions", functions);
                        }
                    }
                    System.out.println("map: " + map);
                }
                if ("Memory".equals(modelTypes[i])) {
                    Map<String, Object> map = (Map<String, Object>) model.getConfigJson();
                    String memoryType = (String) map.get("type");
                    if ("mem_local_short".equals(memoryType) || "short_long_memory".equals(memoryType)) {
                        memLocalShortLLMModelId = (String) map.get("llm");
                        if (StringUtils.isNotBlank(memLocalShortLLMModelId)
                                && memLocalShortLLMModelId.equals(llmModelId)) {
                            memLocalShortLLMModelId = null;
                        }
                    }
                }
                // 如果是LLM类型，且intentLLMModelId不为空，则添加附加模型
                if ("LLM".equals(modelTypes[i])) {
                    if (StringUtils.isNotBlank(intentLLMModelId)) {
                        if (!typeConfig.containsKey(intentLLMModelId)) {
                            // 修改这里：添加isMaskSensitive=false参数
                            ModelConfigEntity intentLLM = modelConfigService.getModelByIdFromCache(intentLLMModelId);
                            typeConfig.put(intentLLM.getId(), intentLLM.getConfigJson());
                        }
                    }
                    if (StringUtils.isNotBlank(memLocalShortLLMModelId)) {
                        if (!typeConfig.containsKey(memLocalShortLLMModelId)) {
                            // 修改这里：添加isMaskSensitive=false参数
                            ModelConfigEntity memLocalShortLLM = modelConfigService
                                    .getModelByIdFromCache(memLocalShortLLMModelId);
                            typeConfig.put(memLocalShortLLM.getId(), memLocalShortLLM.getConfigJson());
                        }
                    }
                }
            }
            result.put(modelTypes[i], typeConfig);

            selectedModule.put(modelTypes[i], model.getId());
        }

        result.put("selected_module", selectedModule);
        if (StringUtils.isNotBlank(prompt)) {
            prompt = prompt.replace("{{assistant_name}}", StringUtils.isBlank(assistantName) ? "小智" : assistantName);
        }
        result.put("prompt", prompt);
        result.put("summaryMemory", summaryMemory);
    }
}
