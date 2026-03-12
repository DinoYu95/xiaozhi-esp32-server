package xiaozhi.modules.agent.service;

import java.util.List;

import xiaozhi.modules.agent.dto.AgentSkillMappingItemDTO;
import xiaozhi.modules.agent.vo.AgentSkillMappingVO;

public interface AgentSkillMappingService {

    List<AgentSkillMappingVO> listByAgentId(String agentId);

    void saveMapping(String agentId, List<AgentSkillMappingItemDTO> items);

    /**
     * 为 agent 添加官方推荐技能（仅当 agent 尚无技能映射时）
     */
    void addOfficialRecommendedSkillsIfEmpty(String agentId);

    /**
     * 为 agent 新增一条 speaker→skill 映射（幂等：已存在则跳过）
     */
    void addMapping(String agentId, String speakerType, String skillId);

    /**
     * 移除 agent 的 speaker→skill 映射
     */
    void removeMapping(String agentId, String speakerType, String skillId);
}
