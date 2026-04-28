package xiaozhi.modules.risk.dao;

import org.apache.ibatis.annotations.Mapper;

import xiaozhi.common.dao.BaseDao;
import xiaozhi.modules.risk.entity.ChildRiskOutboxEntity;

@Mapper
public interface ChildRiskOutboxDao extends BaseDao<ChildRiskOutboxEntity> {
}
