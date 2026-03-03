package xiaozhi.modules.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;

@Data
@Schema(description = "说话人类型→技能映射项")
public class AgentSkillMappingItemDTO {

    @NotBlank
    @Schema(description = "说话人类型: owner_child/parent/other_child/other_adult/unknown", requiredMode = Schema.RequiredMode.REQUIRED)
    private String speakerType;

    @NotBlank
    @Schema(description = "技能ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String skillId;
}
