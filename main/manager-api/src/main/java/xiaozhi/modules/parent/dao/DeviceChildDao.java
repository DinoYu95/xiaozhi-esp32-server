package xiaozhi.modules.parent.dao;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import xiaozhi.modules.parent.entity.DeviceChildEntity;

/**
 * 设备主孩子 Dao
 */
@Mapper
public interface DeviceChildDao extends BaseMapper<DeviceChildEntity> {
}
