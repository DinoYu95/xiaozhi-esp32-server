package xiaozhi.modules.parent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "更新进行中的影子任务（仅 status=active 可改）")
public class ParentShadowMissionUpdateDTO {

    @Size(max = 128)
    @Schema(description = "短标题；不传表示不修改")
    private String title;

    @Size(max = 2000)
    @Schema(description = "详细说明；不传表示不修改")
    private String instructions;

    @Min(5)
    @Max(180)
    @Schema(description = "从当前时刻起重新计算截止时间（分钟）；不传表示不修改 endsAt")
    private Integer durationMinutes;
}
