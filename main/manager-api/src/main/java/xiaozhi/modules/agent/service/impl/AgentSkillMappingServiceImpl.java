package xiaozhi.modules.agent.service.impl;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;

import lombok.AllArgsConstructor;
import xiaozhi.common.constant.Constant;
import xiaozhi.common.utils.ConvertUtils;
import xiaozhi.common.utils.JsonUtils;
import xiaozhi.modules.agent.dao.AgentSkillMappingDao;
import xiaozhi.modules.agent.dto.AgentSkillMappingItemDTO;
import xiaozhi.modules.agent.entity.AgentSkillMappingEntity;
import xiaozhi.modules.agent.service.AgentSkillMappingService;
import xiaozhi.modules.agent.service.AgentSkillService;
import xiaozhi.modules.agent.vo.AgentSkillMappingVO;
import xiaozhi.modules.agent.vo.AgentSkillVO;
import xiaozhi.modules.sys.service.SysParamsService;

@Service
@AllArgsConstructor
public class AgentSkillMappingServiceImpl implements AgentSkillMappingService {

    private static final String SPEAKER_OWNER_CHILD = "owner_child";

    private final AgentSkillMappingDao agentSkillMappingDao;
    private final AgentSkillService agentSkillService;
    private final SysParamsService sysParamsService;

    @Override
    public List<AgentSkillMappingVO> listByAgentId(String agentId) {
        if (StringUtils.isBlank(agentId)) {
            return List.of();
        }
        List<AgentSkillMappingEntity> list = agentSkillMappingDao.selectList(
                new LambdaQueryWrapper<AgentSkillMappingEntity>().eq(AgentSkillMappingEntity::getAgentId, agentId));
        return ConvertUtils.sourceToTarget(list, AgentSkillMappingVO.class);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveMapping(String agentId, List<AgentSkillMappingItemDTO> items) {
        if (StringUtils.isBlank(agentId)) {
            return;
        }
        agentSkillMappingDao.delete(new LambdaQueryWrapper<AgentSkillMappingEntity>()
                .eq(AgentSkillMappingEntity::getAgentId, agentId));
        if (items == null || items.isEmpty()) {
            return;
        }
        Date now = new Date();
        for (AgentSkillMappingItemDTO item : items) {
            if (StringUtils.isBlank(item.getSpeakerType()) || StringUtils.isBlank(item.getSkillId())) {
                continue;
            }
            AgentSkillMappingEntity entity = new AgentSkillMappingEntity();
            entity.setAgentId(agentId);
            entity.setSpeakerType(item.getSpeakerType());
            entity.setSkillId(item.getSkillId());
            entity.setCreateTime(now);
            entity.setUpdateTime(now);
            agentSkillMappingDao.insert(entity);
        }
    }

    @Override
    public void addMapping(String agentId, String speakerType, String skillId) {
        if (StringUtils.isBlank(agentId) || StringUtils.isBlank(speakerType) || StringUtils.isBlank(skillId)) {
            return;
        }
        long cnt = agentSkillMappingDao.selectCount(
                new LambdaQueryWrapper<AgentSkillMappingEntity>()
                        .eq(AgentSkillMappingEntity::getAgentId, agentId)
                        .eq(AgentSkillMappingEntity::getSpeakerType, speakerType)
                        .eq(AgentSkillMappingEntity::getSkillId, skillId));
        if (cnt > 0) {
            return;
        }
        Date now = new Date();
        AgentSkillMappingEntity entity = new AgentSkillMappingEntity();
        entity.setAgentId(agentId);
        entity.setSpeakerType(speakerType);
        entity.setSkillId(skillId);
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        agentSkillMappingDao.insert(entity);
    }

    @Override
    public void removeMapping(String agentId, String speakerType, String skillId) {
        if (StringUtils.isBlank(agentId)) {
            return;
        }
        LambdaQueryWrapper<AgentSkillMappingEntity> wrapper = new LambdaQueryWrapper<AgentSkillMappingEntity>()
                .eq(AgentSkillMappingEntity::getAgentId, agentId);
        if (StringUtils.isNotBlank(speakerType)) {
            wrapper.eq(AgentSkillMappingEntity::getSpeakerType, speakerType);
        }
        if (StringUtils.isNotBlank(skillId)) {
            wrapper.eq(AgentSkillMappingEntity::getSkillId, skillId);
        }
        agentSkillMappingDao.delete(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addOfficialRecommendedSkillsIfEmpty(String agentId) {
        if (StringUtils.isBlank(agentId)) {
            return;
        }
        List<AgentSkillMappingEntity> existing = agentSkillMappingDao.selectList(
                new LambdaQueryWrapper<AgentSkillMappingEntity>().eq(AgentSkillMappingEntity::getAgentId, agentId));
        if (existing != null && !existing.isEmpty()) {
            return;
        }

        List<AgentSkillVO> recommended = agentSkillService.listOfficialRecommended();
        if (recommended != null) {
            for (AgentSkillVO skill : recommended) {
                if (StringUtils.isNotBlank(skill.getId())) {
                    addMapping(agentId, SPEAKER_OWNER_CHILD, skill.getId());
                }
            }
        }

        Map<String, List<String>> defaultsBySpeaker = loadDefaultSkillMappingBySpeaker();
        for (Map.Entry<String, List<String>> entry : defaultsBySpeaker.entrySet()) {
            String speakerType = entry.getKey();
            if (SPEAKER_OWNER_CHILD.equals(speakerType)) {
                continue;
            }
            List<String> skillIds = entry.getValue();
            if (skillIds == null) {
                continue;
            }
            for (String skillId : skillIds) {
                if (StringUtils.isBlank(skillId)) {
                    continue;
                }
                if (agentSkillService.getById(skillId) != null) {
                    addMapping(agentId, speakerType, skillId);
                }
            }
        }
    }

    private Map<String, List<String>> loadDefaultSkillMappingBySpeaker() {
        String json = sysParamsService.getValue(Constant.SERVER_AGENT_DEFAULT_SKILL_MAPPING, true);
        if (StringUtils.isNotBlank(json)) {
            try {
                Map<String, List<String>> parsed = JsonUtils.parseObject(json,
                        new TypeReference<Map<String, List<String>>>() {
                        });
                if (parsed != null && !parsed.isEmpty()) {
                    return parsed;
                }
            } catch (Exception ignored) {
                // fall through to built-in defaults
            }
        }
        return builtInDefaultSkillMappingBySpeaker();
    }

    /** 与智控台文档建议一致；skill_id 须在 ai_skill 中存在才会写入 */
    private static Map<String, List<String>> builtInDefaultSkillMappingBySpeaker() {
        Map<String, List<String>> map = new LinkedHashMap<>();
        map.put("parent", List.of("skill_parent_assistant"));
        map.put("other_child", List.of("skill_guest_child"));
        map.put("other_adult", List.of("skill_guest_adult"));
        map.put("unknown", List.of("skill_general_chat"));
        return map;
    }
}
