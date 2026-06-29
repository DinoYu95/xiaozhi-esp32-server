package xiaozhi.modules.agent.service;

import java.util.List;

import com.baomidou.mybatisplus.extension.service.IService;

import xiaozhi.modules.agent.dto.AgentSkillSaveDTO;
import xiaozhi.modules.agent.entity.AgentSkillEntity;
import xiaozhi.modules.agent.vo.AgentSkillVO;

public interface AgentSkillService extends IService<AgentSkillEntity> {

    List<AgentSkillVO> listAll();

    /**
     * 官方推荐的技能列表（家长端展示）
     */
    List<AgentSkillVO> listOfficialRecommended();

    /**
     * 官方推荐技能按关键词模糊搜索（name、description）
     */
    List<AgentSkillVO> searchOfficialRecommended(String keyword);

    AgentSkillVO getById(String id);

    boolean saveSkill(AgentSkillSaveDTO dto);

    boolean updateSkill(AgentSkillSaveDTO dto);

    boolean removeById(String id);

    /** 意图未匹配时的全局默认兜底 skill_id */
    String getDefaultFallbackSkillId();
}
