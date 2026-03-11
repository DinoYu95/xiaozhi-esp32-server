package xiaozhi.modules.parent.service;

import java.util.List;

import xiaozhi.modules.parent.dto.ParentUserSkillSaveDTO;
import xiaozhi.modules.parent.vo.ParentUserSkillVO;

/**
 * 家长端自定义技能服务
 */
public interface ParentUserSkillService {

    /**
     * 查询当前家长添加的所有技能
     */
    List<ParentUserSkillVO> listByParentUserId(Long parentUserId);

    /**
     * 按 id 获取家长技能（用于设备已绑定技能展示）
     */
    ParentUserSkillVO getById(Long id);

    /**
     * 家长技能按关键词模糊搜索（name、description）
     */
    List<ParentUserSkillVO> searchByParentUserId(Long parentUserId, String keyword);

    /**
     * 创建技能
     */
    ParentUserSkillVO create(Long parentUserId, ParentUserSkillSaveDTO dto);

    /**
     * 更新技能（校验归属）
     */
    ParentUserSkillVO update(Long parentUserId, Long id, ParentUserSkillSaveDTO dto);

    /**
     * 删除技能（校验归属）
     */
    void delete(Long parentUserId, Long id);
}
