package xiaozhi.modules.agent.service;

import java.util.List;

import xiaozhi.modules.agent.dto.AgentSkillMappingItemDTO;
import xiaozhi.modules.agent.vo.AgentSkillMappingVO;

public interface AgentSkillMappingService {

    List<AgentSkillMappingVO> listByAgentId(String agentId);

    void saveMapping(String agentId, List<AgentSkillMappingItemDTO> items);

    /**
     * 为 agent 写入默认「说话人→技能」映射（仅当 agent 尚无映射时）：
     * owner_child = 全部官方推荐；其余说话人见 sys_params server.agent_default_skill_mapping
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
