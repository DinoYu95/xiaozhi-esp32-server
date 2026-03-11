package xiaozhi.modules.device.dao;

import java.util.Date;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import xiaozhi.modules.device.entity.DeviceEntity;

@Mapper
public interface DeviceDao extends BaseMapper<DeviceEntity> {
    /**
     * 获取此智能体全部设备的最后连接时间
     * 
     * @param agentId 智能体id
     * @return
     */
    Date getAllLastConnectedAtByAgentId(String agentId);

    /**
     * 按 id 或 mac 格式变体查找设备（兼容 B6:C8:35:D6:10:48 / b6_c8_35_d6_10_48）
     */
    DeviceEntity selectByIdOrMacVariant(String deviceId);
}