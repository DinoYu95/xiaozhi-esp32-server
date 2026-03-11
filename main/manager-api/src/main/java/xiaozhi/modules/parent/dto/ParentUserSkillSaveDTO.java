package xiaozhi.modules.parent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "家长端创建/更新技能")
public class ParentUserSkillSaveDTO {

    @NotBlank
    @Schema(description = "技能名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Schema(description = "简短用途说明")
    private String description;

    @NotBlank
    @Schema(description = "技能说明/系统级提示", requiredMode = Schema.RequiredMode.REQUIRED)
    private String instructions;

    @Schema(description = "版本号")
    private String version;

    @Schema(description = "工具 id 列表，JSON 数组字符串")
    private String tools;

    @Schema(description = "扩展字段，JSON 对象字符串")
    private String metadata;
}
