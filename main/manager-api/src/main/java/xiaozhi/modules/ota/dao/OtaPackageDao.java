package xiaozhi.modules.ota.dao;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import xiaozhi.modules.ota.entity.OtaPackageEntity;

@Mapper
public interface OtaPackageDao extends BaseMapper<OtaPackageEntity> {
}
