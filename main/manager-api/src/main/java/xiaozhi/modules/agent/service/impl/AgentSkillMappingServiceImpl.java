package xiaozhi.modules.agent.service.impl;

import java.util.Date;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import lombok.AllArgsConstructor;
import xiaozhi.common.utils.ConvertUtils;
import xiaozhi.modules.agent.dao.AgentSkillMappingDao;
import xiaozhi.modules.agent.dto.AgentSkillMappingItemDTO;
import xiaozhi.modules.agent.entity.AgentSkillMappingEntity;
import xiaozhi.modules.agent.service.AgentSkillMappingService;
import xiaozhi.modules.agent.service.AgentSkillService;
import xiaozhi.modules.agent.vo.AgentSkillMappingVO;
import xiaozhi.modules.agent.vo.AgentSkillVO;

@Service
@AllArgsConstructor
public class AgentSkillMappingServiceImpl implements AgentSkillMappingService {

    private static final String DEFAULT_SPEAKER_TYPE = "owner_child";

    private final AgentSkillMappingDao agentSkillMappingDao;
    private final AgentSkillService agentSkillService;

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
            return; // 已存在，幂等
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
    public void addOfficialRecommendedSkillsIfEmpty(String agentId) {
        if (StringUtils.isBlank(agentId)) {
            return;
        }
        List<AgentSkillMappingEntity> existing = agentSkillMappingDao.selectList(
                new LambdaQueryWrapper<AgentSkillMappingEntity>().eq(AgentSkillMappingEntity::getAgentId, agentId));
        if (existing != null && !existing.isEmpty()) {
            return;
        }
        List<AgentSkillVO> skills = agentSkillService.listOfficialRecommended();
        if (skills == null || skills.isEmpty()) {
            return;
        }
        Date now = new Date();
        for (AgentSkillVO skill : skills) {
            if (StringUtils.isBlank(skill.getId())) {
                continue;
            }
            AgentSkillMappingEntity entity = new AgentSkillMappingEntity();
            entity.setAgentId(agentId);
            entity.setSpeakerType(DEFAULT_SPEAKER_TYPE);
            entity.setSkillId(skill.getId());
            entity.setCreateTime(now);
            entity.setUpdateTime(now);
            agentSkillMappingDao.insert(entity);
        }
    }
}
