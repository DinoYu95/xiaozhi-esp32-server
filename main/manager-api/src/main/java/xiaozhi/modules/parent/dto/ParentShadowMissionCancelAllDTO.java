package xiaozhi.modules.parent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "取消某孩子全部进行中的影子任务")
public class ParentShadowMissionCancelAllDTO {

    @NotNull
    @Schema(description = "孩子主键 device_child.id", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long childId;
}
