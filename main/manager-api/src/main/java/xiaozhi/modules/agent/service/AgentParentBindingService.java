package xiaozhi.modules.agent.service;

import java.util.List;

import xiaozhi.modules.agent.dto.AgentBindParentDTO;
import xiaozhi.modules.agent.dto.AgentDTO;
import xiaozhi.modules.agent.vo.ParentUserSearchVO;

public interface AgentParentBindingService {

    String ACTIVATION_ALL = "all";
    String ACTIVATION_ACTIVE = "active";
    String ACTIVATION_INACTIVE = "inactive";

    void enrichParentBinding(List<AgentDTO> agents);

    List<AgentDTO> filterByActivation(List<AgentDTO> agents, String activationFilter);

    List<ParentUserSearchVO> searchParentUsers(String keyword);

    void adminBindParent(Long scopeUserId, String agentId, AgentBindParentDTO dto);
}
