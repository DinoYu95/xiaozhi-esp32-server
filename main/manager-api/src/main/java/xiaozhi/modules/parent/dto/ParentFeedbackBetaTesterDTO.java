package xiaozhi.modules.parent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "设置家长是否为内测用户")
public class ParentFeedbackBetaTesterDTO {

    @NotNull
    @Schema(description = "家长用户 id", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long parentUserId;

    @NotNull
    @Schema(description = "true=内测用户", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean betaTester;
}
