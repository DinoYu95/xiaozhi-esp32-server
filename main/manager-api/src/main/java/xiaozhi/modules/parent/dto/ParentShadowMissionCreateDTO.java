package xiaozhi.modules.parent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "创建影子任务")
public class ParentShadowMissionCreateDTO {

    @NotNull
    @Schema(description = "孩子主键 device_child.id", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long childId;

    @NotBlank
    @Size(max = 128)
    @Schema(description = "短标题", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @NotBlank
    @Size(max = 2000)
    @Schema(description = "详细说明", requiredMode = Schema.RequiredMode.REQUIRED)
    private String instructions;

    @Min(5)
    @Max(180)
    @Schema(description = "有效时长（分钟），默认 30，范围 5～180")
    private Integer durationMinutes = 30;
}
