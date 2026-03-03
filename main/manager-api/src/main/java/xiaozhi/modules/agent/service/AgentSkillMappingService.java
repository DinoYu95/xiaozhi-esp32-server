package xiaozhi.modules.agent.service;

import java.util.List;

import xiaozhi.modules.agent.dto.AgentSkillMappingItemDTO;
import xiaozhi.modules.agent.vo.AgentSkillMappingVO;

public interface AgentSkillMappingService {

    List<AgentSkillMappingVO> listByAgentId(String agentId);

    void saveMapping(String agentId, List<AgentSkillMappingItemDTO> items);
}
