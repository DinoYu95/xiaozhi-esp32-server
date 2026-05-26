package xiaozhi.modules.parent.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "AI 生成的技能草稿，供家长预览确认后再创建")
public class ParentSkillDraftVO {

    @Schema(description = "建议的能力名称（展示用）")
    private String name;

    @Schema(description = "建议说明：孩子聊到什么时机器人会怎样（面向家长）")
    private String description;

    @Schema(description = "触发场景与聊天方式（系统内部指令，创建后存入 instructions）")
    private String instructions;

    @Schema(description = "面向家长的触发说明，如：当孩子说「讲个故事」时启用")
    private String triggerHint;
}
