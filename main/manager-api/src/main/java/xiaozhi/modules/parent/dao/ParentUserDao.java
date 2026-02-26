package xiaozhi.modules.parent.dao;

import org.apache.ibatis.annotations.Mapper;

import xiaozhi.common.dao.BaseDao;
import xiaozhi.modules.parent.entity.ParentUserEntity;

@Mapper
public interface ParentUserDao extends BaseDao<ParentUserEntity> {
}
