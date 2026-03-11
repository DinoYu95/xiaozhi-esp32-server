package xiaozhi.modules.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;

@Data
@Schema(description = "创建/更新技能")
public class AgentSkillSaveDTO {

    @NotBlank
    @Schema(description = "技能唯一标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private String id;

    @NotBlank
    @Schema(description = "展示名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Schema(description = "简短用途说明")
    private String description;

    @NotBlank
    @Schema(description = "技能说明/系统级提示", requiredMode = Schema.RequiredMode.REQUIRED)
    private String instructions;

    @Schema(description = "版本号")
    private String version;

    @Schema(description = "工具 id 列表，JSON 数组字符串，如 [\"play_music\"]")
    private String tools;

    @Schema(description = "扩展字段，JSON 对象字符串")
    private String metadata;

    @Schema(description = "是否官方推荐：true是 false否，家长端展示推荐技能")
    private Boolean isOfficialRecommended;
}
