package xiaozhi.modules.ota.dao;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import xiaozhi.modules.ota.entity.OtaDeviceUpgradeLogEntity;

@Mapper
public interface OtaDeviceUpgradeLogDao extends BaseMapper<OtaDeviceUpgradeLogEntity> {
}
