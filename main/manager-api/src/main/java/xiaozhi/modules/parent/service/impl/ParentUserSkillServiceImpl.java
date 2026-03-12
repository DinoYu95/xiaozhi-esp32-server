package xiaozhi.modules.parent.service.impl;

import java.util.Date;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import lombok.RequiredArgsConstructor;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.utils.ConvertUtils;
import xiaozhi.modules.parent.dao.ParentUserSkillDao;
import xiaozhi.modules.parent.dto.ParentUserSkillSaveDTO;
import xiaozhi.modules.parent.entity.ParentUserSkillEntity;
import xiaozhi.modules.parent.service.ParentUserSkillService;
import xiaozhi.modules.parent.vo.ParentUserSkillVO;

@Service
@RequiredArgsConstructor
public class ParentUserSkillServiceImpl implements ParentUserSkillService {

    private final ParentUserSkillDao parentUserSkillDao;

    @Override
    public ParentUserSkillVO getById(Long id) {
        if (id == null) return null;
        ParentUserSkillEntity entity = parentUserSkillDao.selectById(id);
        return entity == null ? null : ConvertUtils.sourceToTarget(entity, ParentUserSkillVO.class);
    }

    @Override
    public ParentUserSkillVO getByIdAndParentUserId(Long id, Long parentUserId) {
        if (id == null || parentUserId == null) return null;
        ParentUserSkillEntity entity = parentUserSkillDao.selectOne(
                new LambdaQueryWrapper<ParentUserSkillEntity>()
                        .eq(ParentUserSkillEntity::getId, id)
                        .eq(ParentUserSkillEntity::getParentUserId, parentUserId));
        return entity == null ? null : ConvertUtils.sourceToTarget(entity, ParentUserSkillVO.class);
    }

    @Override
    public List<ParentUserSkillVO> listByParentUserId(Long parentUserId) {
        List<ParentUserSkillEntity> list = parentUserSkillDao.selectList(
                new LambdaQueryWrapper<ParentUserSkillEntity>()
                        .eq(ParentUserSkillEntity::getParentUserId, parentUserId)
                        .orderByDesc(ParentUserSkillEntity::getCreateTime));
        return ConvertUtils.sourceToTarget(list, ParentUserSkillVO.class);
    }

    @Override
    public List<ParentUserSkillVO> searchByParentUserId(Long parentUserId, String keyword) {
        LambdaQueryWrapper<ParentUserSkillEntity> wrapper = new LambdaQueryWrapper<ParentUserSkillEntity>()
                .eq(ParentUserSkillEntity::getParentUserId, parentUserId)
                .orderByDesc(ParentUserSkillEntity::getCreateTime);
        if (StringUtils.isNotBlank(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(ParentUserSkillEntity::getName, kw).or().like(ParentUserSkillEntity::getDescription, kw));
        }
        List<ParentUserSkillEntity> list = parentUserSkillDao.selectList(wrapper);
        return ConvertUtils.sourceToTarget(list, ParentUserSkillVO.class);
    }

    @Override
    public ParentUserSkillVO create(Long parentUserId, ParentUserSkillSaveDTO dto) {
        ParentUserSkillEntity entity = ConvertUtils.sourceToTarget(dto, ParentUserSkillEntity.class);
        entity.setParentUserId(parentUserId);
        entity.setCreateTime(new Date());
        entity.setUpdateTime(new Date());
        if (StringUtils.isBlank(entity.getVersion())) {
            entity.setVersion("1.0");
        }
        parentUserSkillDao.insert(entity);
        return ConvertUtils.sourceToTarget(entity, ParentUserSkillVO.class);
    }

    @Override
    public ParentUserSkillVO update(Long parentUserId, Long id, ParentUserSkillSaveDTO dto) {
        ParentUserSkillEntity entity = parentUserSkillDao.selectById(id);
        if (entity == null || !entity.getParentUserId().equals(parentUserId)) {
            throw new RenException(ErrorCode.PARENT_SKILL_NOT_FOUND);
        }
        entity.setName(dto.getName());
        entity.setDescription(dto.getDescription());
        entity.setInstructions(dto.getInstructions());
        entity.setVersion(StringUtils.isNotBlank(dto.getVersion()) ? dto.getVersion() : entity.getVersion());
        entity.setTools(dto.getTools());
        entity.setMetadata(dto.getMetadata());
        entity.setUpdateTime(new Date());
        parentUserSkillDao.updateById(entity);
        return ConvertUtils.sourceToTarget(entity, ParentUserSkillVO.class);
    }

    @Override
    public void delete(Long parentUserId, Long id) {
        ParentUserSkillEntity entity = parentUserSkillDao.selectById(id);
        if (entity == null || !entity.getParentUserId().equals(parentUserId)) {
            throw new RenException(ErrorCode.PARENT_SKILL_NOT_FOUND);
        }
        parentUserSkillDao.deleteById(id);
    }
}
