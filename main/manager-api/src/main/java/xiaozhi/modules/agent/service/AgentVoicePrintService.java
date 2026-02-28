package xiaozhi.modules.agent.service;

import java.util.List;

import xiaozhi.modules.agent.dto.AgentVoicePrintSaveDTO;
import xiaozhi.modules.agent.entity.AgentVoicePrintEntity;
import xiaozhi.modules.agent.dto.AgentVoicePrintUpdateDTO;
import xiaozhi.modules.agent.vo.AgentVoicePrintVO;

/**
 * 智能体声纹处理service
 *
 * @author zjy
 */
public interface AgentVoicePrintService {
    /**
     * 添加智能体新的声纹
     *
     * @param dto 保存智能体声纹的数据
     * @return T:成功 F：失败
     */
    boolean insert(AgentVoicePrintSaveDTO dto);

    /**
     * 删除智能体的指的声纹
     *
     * @param userId       当前登录的用户id
     * @param voicePrintId 声纹id
     * @return 是否成功 T:成功 F：失败
     */
    boolean delete(Long userId, String voicePrintId);

    /**
     * 获取指定智能体的所有声纹数据
     *
     * @param userId  当前登录的用户id
     * @param agentId 智能体id
     * @return 声纹数据集合
     */
    List<AgentVoicePrintVO> list(Long userId, String agentId);

    /**
     * 更新智能体的指的声纹数据
     *
     * @param userId 当前登录的用户id
     * @param dto    修改的声纹的数据
     * @return 是否成功 T:成功 F：失败
     */
    boolean update(Long userId, AgentVoicePrintUpdateDTO dto);

    /**
     * 按孩子ID物理删除其所有声纹（并调用声纹服务 cancel）
     *
     * @param childId device_child.id
     */
    void deleteByChildId(Long childId);

    /**
     * 添加或更新主孩子声纹（一孩一声纹：同 child+agent 已存在则更新）
     *
     * @param agentId    智能体ID
     * @param childId    孩子ID
     * @param audioId    上传得到的音频ID（ai_agent_chat_audio.id）
     * @param sourceName 声纹来源姓名
     * @param introduce  描述
     */
    void saveChildVoicePrint(String agentId, Long childId, String audioId, String sourceName, String introduce);

    /**
     * 按声纹ID删除（不校验 creator，用于家长端删除孩子声纹时，校验由调用方完成）
     *
     * @param voicePrintId 声纹ID
     */
    void deleteByVoicePrintId(String voicePrintId);

    /**
     * 按智能体+孩子查询声纹（主孩子在该 agent 下至多一条）
     *
     * @param agentId 智能体ID
     * @param childId 孩子ID
     * @return 声纹列表，0 或 1 条
     */
    List<AgentVoicePrintVO> listByAgentIdAndChildId(String agentId, Long childId);

    /**
     * 按ID查询声纹（用于校验 child_id 等）
     */
    AgentVoicePrintEntity getById(String voicePrintId);
}
