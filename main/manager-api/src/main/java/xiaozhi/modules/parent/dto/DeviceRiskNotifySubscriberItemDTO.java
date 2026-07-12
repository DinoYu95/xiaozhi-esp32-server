package xiaozhi.modules.parent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "单个 Member 风险通知开关")
public class DeviceRiskNotifySubscriberItemDTO {

    @NotNull
    @Schema(description = "家长 parent_user.id")
    private Long parentId;

    @NotNull
    @Schema(description = "是否接收风险提示")
    private Boolean receiveRiskNotify;
}
