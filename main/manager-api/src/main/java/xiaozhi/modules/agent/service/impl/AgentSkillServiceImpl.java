package xiaozhi.modules.agent.service.impl;

import java.util.Date;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import xiaozhi.common.utils.ConvertUtils;
import xiaozhi.modules.agent.dao.AgentSkillDao;
import xiaozhi.modules.agent.dto.AgentSkillSaveDTO;
import xiaozhi.modules.agent.entity.AgentSkillEntity;
import xiaozhi.modules.agent.service.AgentSkillService;
import xiaozhi.modules.agent.vo.AgentSkillVO;

@Service
public class AgentSkillServiceImpl extends ServiceImpl<AgentSkillDao, AgentSkillEntity> implements AgentSkillService {

    @Override
    public List<AgentSkillVO> listAll() {
        List<AgentSkillEntity> list = baseMapper.selectList(
                new LambdaQueryWrapper<AgentSkillEntity>().orderByAsc(AgentSkillEntity::getId));
        return ConvertUtils.sourceToTarget(list, AgentSkillVO.class);
    }

    @Override
    public AgentSkillVO getById(String id) {
        AgentSkillEntity entity = baseMapper.selectById(id);
        return entity == null ? null : ConvertUtils.sourceToTarget(entity, AgentSkillVO.class);
    }

    @Override
    public boolean saveSkill(AgentSkillSaveDTO dto) {
        if (baseMapper.selectById(dto.getId()) != null) {
            return false;
        }
        AgentSkillEntity entity = ConvertUtils.sourceToTarget(dto, AgentSkillEntity.class);
        entity.setCreateTime(new Date());
        entity.setUpdateTime(new Date());
        if (StringUtils.isBlank(entity.getVersion())) {
            entity.setVersion("1.0");
        }
        return baseMapper.insert(entity) > 0;
    }

    @Override
    public boolean updateSkill(AgentSkillSaveDTO dto) {
        AgentSkillEntity entity = baseMapper.selectById(dto.getId());
        if (entity == null) {
            return false;
        }
        entity.setName(dto.getName());
        entity.setDescription(dto.getDescription());
        entity.setInstructions(dto.getInstructions());
        entity.setVersion(StringUtils.isNotBlank(dto.getVersion()) ? dto.getVersion() : entity.getVersion());
        entity.setTools(dto.getTools());
        entity.setMetadata(dto.getMetadata());
        entity.setUpdateTime(new Date());
        return baseMapper.updateById(entity) > 0;
    }

    @Override
    public boolean removeById(String id) {
        return baseMapper.deleteById(id) > 0;
    }
}
