package xiaozhi.modules.growthportrait.dao;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import xiaozhi.modules.growthportrait.entity.LearnerGrowthStateEntity;

@Mapper
public interface LearnerGrowthStateDao extends BaseMapper<LearnerGrowthStateEntity> {
}
