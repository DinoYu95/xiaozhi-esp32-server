package xiaozhi.modules.learning.dao;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import xiaozhi.modules.learning.entity.KgNodeEntity;

@Mapper
public interface KgNodeDao extends BaseMapper<KgNodeEntity> {
}
