package xiaozhi.modules.parent.vo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import xiaozhi.modules.agent.vo.AgentSkillVO;

/**
 * 家长端技能搜索结果（家长自定义 + 官方推荐分开展示）
 */
@Data
@Schema(description = "技能搜索结果")
public class ParentSkillSearchVO {

    @Schema(description = "家长自己创建的技能")
    private List<ParentUserSkillVO> parentSkills;

    @Schema(description = "官方推荐的技能")
    private List<AgentSkillVO> recommendedSkills;
}
