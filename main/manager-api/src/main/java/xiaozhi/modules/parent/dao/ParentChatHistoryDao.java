package xiaozhi.modules.parent.dao;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import xiaozhi.modules.parent.entity.ParentChatHistoryEntity;

/**
 * 家长聊天记录 Dao
 */
@Mapper
public interface ParentChatHistoryDao extends BaseMapper<ParentChatHistoryEntity> {
}
