package xiaozhi.modules.parent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 家长端-绑定技能到设备（支持按 speaker  targeting）
 */
@Data
@Schema(description = "绑定技能到设备")
public class ParentDeviceSkillBindDTO {

    @NotBlank(message = "技能来源不能为空")
    @Schema(description = "技能来源：official 官方推荐，parent 家长自建", requiredMode = Schema.RequiredMode.REQUIRED)
    private String skillSource;

    @Schema(description = "技能ID：官方=ai_skill.id(string)，家长=parent_user_skill.id(number)，与 search/已绑定列表的 skillId 一致", requiredMode = Schema.RequiredMode.REQUIRED)
    private Object skillId;

    @NotBlank(message = "说话人类型不能为空")
    @Schema(description = "说话人类型: owner_child/parent/other_child/other_adult/unknown", requiredMode = Schema.RequiredMode.REQUIRED)
    private String speakerType;
}
