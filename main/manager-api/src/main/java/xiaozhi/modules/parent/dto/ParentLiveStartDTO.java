package xiaozhi.modules.parent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "发起远程实时监控")
public class ParentLiveStartDTO {

    @NotBlank
    @Schema(description = "设备 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String deviceId;

    @Schema(description = "可选孩子 ID")
    private Long childId;
}
