package xiaozhi.modules.parent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "确认提交家长风险观察")
public class ParentRiskWatchCreateDTO {

    @NotNull
    private Long childId;

    @NotBlank
    @Schema(description = "KEYWORD 或 EVALUATOR")
    private String watchType;

    @NotBlank
    private String riskDomain;

    @NotBlank
    @Size(max = 128)
    private String name;

    @Size(max = 512)
    private String description;

    @Size(max = 256)
    private String triggerHint;

    @Size(max = 512)
    private String pattern;

    private Integer riskLevel;

    @Size(max = 64)
    private String category;

    private String instructions;

    @Size(max = 512)
    private String allowedCategories;
}
