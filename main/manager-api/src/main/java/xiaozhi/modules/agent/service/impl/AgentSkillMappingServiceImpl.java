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
import xiaozhi.modules.agent.vo.AgentSkillMappingVO;

@Service
@AllArgsConstructor
public class AgentSkillMappingServiceImpl implements AgentSkillMappingService {

    private final AgentSkillMappingDao agentSkillMappingDao;

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
}
