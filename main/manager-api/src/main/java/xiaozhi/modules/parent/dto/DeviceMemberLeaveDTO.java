package xiaozhi.modules.parent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Member 主动退出设备")
public class DeviceMemberLeaveDTO {
    @Schema(description = "设备 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String deviceId;
}
