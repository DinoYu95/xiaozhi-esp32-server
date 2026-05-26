package xiaozhi.modules.parent.service;

import xiaozhi.modules.parent.dto.ParentSkillDraftFields;
import xiaozhi.modules.parent.vo.ParentSkillDraftVO;

/**
 * 家长技能 AI 辅助：将自然语言意图转为标准技能配置
 */
public interface ParentSkillAssistService {

    /**
     * 根据家长描述生成技能草稿（名称、描述、指令）
     *
     * @param userIntent     家长自然语言描述
     * @param refinement     可选修改意见
     * @param previousDraft  可选上一轮草稿
     */
    ParentSkillDraftVO generateDraft(String userIntent, String refinement, ParentSkillDraftFields previousDraft);
}
