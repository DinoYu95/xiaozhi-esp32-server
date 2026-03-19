package xiaozhi.modules.parent.dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import xiaozhi.modules.parent.entity.ParentDeviceRuleEntity;

@Mapper
public interface ParentDeviceRuleDao extends BaseMapper<ParentDeviceRuleEntity> {

    /**
     * 查询设备规则列表（按创建时间倒序，最多返回 limit 条）
     */
    List<ParentDeviceRuleEntity> selectByDeviceId(String deviceId, int limit);
}
