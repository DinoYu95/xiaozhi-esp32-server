package xiaozhi.modules.agent.service;

import java.util.List;

import com.baomidou.mybatisplus.extension.service.IService;

import xiaozhi.modules.agent.dto.AgentSkillSaveDTO;
import xiaozhi.modules.agent.entity.AgentSkillEntity;
import xiaozhi.modules.agent.vo.AgentSkillVO;

public interface AgentSkillService extends IService<AgentSkillEntity> {

    List<AgentSkillVO> listAll();

    AgentSkillVO getById(String id);

    boolean saveSkill(AgentSkillSaveDTO dto);

    boolean updateSkill(AgentSkillSaveDTO dto);

    boolean removeById(String id);
}
