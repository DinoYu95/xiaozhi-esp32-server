package xiaozhi.modules.agent.service;

import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.extension.service.IService;

import xiaozhi.common.page.PageData;
import xiaozhi.modules.agent.dto.AgentChatHistoryDTO;
import xiaozhi.modules.agent.dto.AgentChatSessionDTO;
import xiaozhi.modules.agent.entity.AgentChatHistoryEntity;
import xiaozhi.modules.agent.vo.AgentChatHistoryUserVO;

/**
 * 智能体聊天记录表处理service
 *
 * @author Goody
 * @version 1.0, 2025/4/30
 * @since 1.0.0
 */
public interface AgentChatHistoryService extends IService<AgentChatHistoryEntity> {

    /**
     * 根据智能体ID获取会话列表
     *
     * @param params 查询参数，包含agentId、page、limit
     * @return 分页的会话列表
     */
    PageData<AgentChatSessionDTO> getSessionListByAgentId(Map<String, Object> params);

    /**
     * 根据会话ID获取聊天记录列表
     *
     * @param agentId   智能体ID
     * @param sessionId 会话ID
     * @return 聊天记录列表
     */
    List<AgentChatHistoryDTO> getChatHistoryBySessionId(String agentId, String sessionId);

    /**
     * 根据智能体ID和设备MAC地址获取近期聊天记录（供家长端汇总孩子与助手的对话）
     *
     * @param agentId    智能体ID
     * @param macAddress 设备MAC地址
     * @param limit      最多返回条数
     * @return 聊天记录列表（按时间正序，便于阅读）
     */
    List<AgentChatHistoryDTO> getRecentByAgentAndMac(String agentId, String macAddress, int limit);

    /**
     * 获取格式化的孩子与助手近期对话（供 zhiban-agent 拉取后回答家长）
     *
     * @param agentId         智能体ID
     * @param macAddress       设备MAC地址
     * @param limit            最多返回条数
     * @param childDisplayName 孩子显示名（如「小明」），用于角色标注，null 则用「孩子」
     * @return 格式化字符串「孩子：xxx\n助手：yyy」，无记录返回空串
     */
    String getFormattedRecentByAgentAndMac(String agentId, String macAddress, int limit, String childDisplayName);

    /**
     * 按 agent_id + mac_address 查询指定日期的聊天记录（供家长端今日简报）
     *
     * @param agentId    智能体ID
     * @param macAddress 设备MAC地址
     * @param dateStart  起始时间（当天 00:00:00）
     * @param dateEnd    结束时间（次日 00:00:00，不包含）
     * @return 聊天记录列表（按时间正序）
     */
    List<AgentChatHistoryDTO> getTodayByAgentAndMac(String agentId, String macAddress,
            java.util.Date dateStart, java.util.Date dateEnd);

    /**
     * 根据智能体ID删除聊天记录
     *
     * @param agentId     智能体ID
     * @param deleteAudio 是否删除音频
     * @param deleteText  是否删除文本
     */
    void deleteByAgentId(String agentId, Boolean deleteAudio, Boolean deleteText);

    /**
     * 根据智能体ID获取最近50条用户的聊天记录数据（带音频数据）
     *
     * @param agentId 智能体id
     * @return 聊天记录列表（只有用户）
     */
    List<AgentChatHistoryUserVO> getRecentlyFiftyByAgentId(String agentId);

    /**
     * 根据音频数据ID获取聊天内容
     *
     * @param audioId 音频id
     * @return 聊天内容
     */
    String getContentByAudioId(String audioId);


    /**
     * 查询此音频id是否属于此智能体
     *
     * @param audioId 音频id
     * @param agentId 音频id
     * @return T：属于 F：不属于
     */
    boolean isAudioOwnedByAgent(String audioId,String agentId);
}
