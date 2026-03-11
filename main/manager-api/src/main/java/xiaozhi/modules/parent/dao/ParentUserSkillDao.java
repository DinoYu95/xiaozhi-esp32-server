package xiaozhi.modules.parent.dao;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import xiaozhi.modules.parent.entity.ParentUserSkillEntity;

/**
 * 家长端自定义技能
 */
@Mapper
public interface ParentUserSkillDao extends BaseMapper<ParentUserSkillEntity> {
}
