package xiaozhi.modules.ota.dao;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import xiaozhi.modules.ota.entity.OtaReleaseEntity;

@Mapper
public interface OtaReleaseDao extends BaseMapper<OtaReleaseEntity> {
}
