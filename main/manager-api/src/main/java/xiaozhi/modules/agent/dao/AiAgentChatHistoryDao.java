package xiaozhi.modules.agent.dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import xiaozhi.modules.agent.entity.AgentChatHistoryEntity;

/**
 * {@link AgentChatHistoryEntity} 智能体聊天历史记录Dao对象
 *
 * @author Goody
 * @version 1.0, 2025/4/30
 * @since 1.0.0
 */
@Mapper
public interface AiAgentChatHistoryDao extends BaseMapper<AgentChatHistoryEntity> {

    /**
     * 根据智能体ID删除聊天历史记录
     *
     * @param agentId 智能体ID
     */
    void deleteHistoryByAgentId(String agentId);

    /**
     * 根据智能体ID删除音频ID
     *
     * @param agentId 智能体ID
     */
    void deleteAudioIdByAgentId(String agentId);

    /**
     * 根据智能体ID获取所有音频ID列表
     *
     * @param agentId 智能体ID
     * @return 音频ID列表
     */
    List<String> getAudioIdsByAgentId(String agentId);

    /**
     * 批量删除音频
     *
     * @param audioIds 音频ID列表
     */
    void deleteAudioByIds(@Param("audioIds") List<String> audioIds);

    /**
     * 按 agent_id + mac_address 查询近期记录，兼容 MAC 格式变体（B6:C8:35:D6:10:48 / b6_c8_35_d6_10_48）
     */
    List<AgentChatHistoryEntity> selectRecentByAgentAndMacVariants(
            @Param("agentId") String agentId,
            @Param("macAddress") String macAddress,
            @Param("limit") int limit);
}
