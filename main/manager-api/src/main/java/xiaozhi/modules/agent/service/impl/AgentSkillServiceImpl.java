package xiaozhi.modules.agent.service.impl;

import java.util.Date;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
    public List<AgentSkillVO> listOfficialRecommended() {
        List<AgentSkillEntity> list = baseMapper.selectList(
                new LambdaQueryWrapper<AgentSkillEntity>()
                        .eq(AgentSkillEntity::getIsOfficialRecommended, 1)
                        .orderByAsc(AgentSkillEntity::getId));
        return ConvertUtils.sourceToTarget(list, AgentSkillVO.class);
    }

    @Override
    public List<AgentSkillVO> searchOfficialRecommended(String keyword) {
        LambdaQueryWrapper<AgentSkillEntity> wrapper = new LambdaQueryWrapper<AgentSkillEntity>()
                .eq(AgentSkillEntity::getIsOfficialRecommended, 1)
                .orderByAsc(AgentSkillEntity::getId);
        if (StringUtils.isNotBlank(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(AgentSkillEntity::getName, kw).or().like(AgentSkillEntity::getDescription, kw));
        }
        List<AgentSkillEntity> list = baseMapper.selectList(wrapper);
        return ConvertUtils.sourceToTarget(list, AgentSkillVO.class);
    }

    @Override
    public AgentSkillVO getById(String id) {
        AgentSkillEntity entity = baseMapper.selectById(id);
        return entity == null ? null : ConvertUtils.sourceToTarget(entity, AgentSkillVO.class);
    }

    @Override
    public String getDefaultFallbackSkillId() {
        AgentSkillEntity entity = baseMapper.selectOne(
                new LambdaQueryWrapper<AgentSkillEntity>()
                        .eq(AgentSkillEntity::getIsDefaultFallback, 1)
                        .last("LIMIT 1"));
        return entity == null ? null : entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
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
        entity.setIsOfficialRecommended(Boolean.TRUE.equals(dto.getIsOfficialRecommended()) ? 1 : 0);
        entity.setIsDefaultFallback(Boolean.TRUE.equals(dto.getIsDefaultFallback()) ? 1 : 0);
        if (entity.getIsDefaultFallback() == 1) {
            clearOtherDefaultFallback(dto.getId());
        }
        return baseMapper.insert(entity) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
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
        entity.setIsOfficialRecommended(Boolean.TRUE.equals(dto.getIsOfficialRecommended()) ? 1 : 0);
        entity.setIsDefaultFallback(Boolean.TRUE.equals(dto.getIsDefaultFallback()) ? 1 : 0);
        entity.setUpdateTime(new Date());
        if (entity.getIsDefaultFallback() == 1) {
            clearOtherDefaultFallback(dto.getId());
        }
        return baseMapper.updateById(entity) > 0;
    }

    @Override
    public boolean removeById(String id) {
        return baseMapper.deleteById(id) > 0;
    }

    private void clearOtherDefaultFallback(String keepSkillId) {
        baseMapper.update(
                null,
                new LambdaUpdateWrapper<AgentSkillEntity>()
                        .set(AgentSkillEntity::getIsDefaultFallback, 0)
                        .eq(AgentSkillEntity::getIsDefaultFallback, 1)
                        .ne(AgentSkillEntity::getId, keepSkillId));
    }
}
