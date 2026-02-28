package xiaozhi.modules.parent.dao;

import org.apache.ibatis.annotations.Mapper;

import xiaozhi.common.dao.BaseDao;
import xiaozhi.modules.parent.entity.ParentAuthEntity;

@Mapper
public interface ParentAuthDao extends BaseDao<ParentAuthEntity> {
}
