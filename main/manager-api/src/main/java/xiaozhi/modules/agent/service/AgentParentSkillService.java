package xiaozhi.modules.agent.service;

import java.util.List;

import xiaozhi.modules.agent.vo.AdminParentUserSkillVO;

/**
 * 后台管理-家长端技能
 */
public interface AgentParentSkillService {

    /**
     * 查询所有家长端技能（含家长昵称）
     */
    List<AdminParentUserSkillVO> listAllForAdmin();

    /**
     * 管理员删除家长端技能
     */
    void deleteByAdmin(Long id);
}
