package xiaozhi.modules.parent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "技能草稿字段（生成结果或二次优化时的上一轮内容）")
public class ParentSkillDraftFields {

    @Schema(description = "技能名称")
    private String name;

    @Schema(description = "简要描述")
    private String description;

    @Schema(description = "技能指令")
    private String instructions;
}
