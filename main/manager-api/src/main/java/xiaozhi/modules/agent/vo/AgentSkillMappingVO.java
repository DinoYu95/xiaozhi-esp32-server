package xiaozhi.modules.agent.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "说话人类型→技能映射")
public class AgentSkillMappingVO {
    private String speakerType;
    private String skillId;
}
