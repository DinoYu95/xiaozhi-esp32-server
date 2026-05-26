package xiaozhi.modules.parent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "根据自然语言描述生成技能草稿")
public class ParentSkillFromIntentDTO {

    @NotBlank
    @Size(min = 5, max = 500)
    @Schema(description = "家长用自然语言描述：当孩子和机器人聊天时，希望在什么场景下、以什么方式陪伴（不是定时任务、不是家长远程指挥）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String userIntent;

    @Size(max = 500)
    @Schema(description = "可选：在已有草稿基础上补充修改意见，如「语气再活泼一点」")
    private String refinement;

    @Schema(description = "可选：上一轮生成的草稿 JSON，与 refinement 配合用于二次优化")
    private ParentSkillDraftFields previousDraft;
}
