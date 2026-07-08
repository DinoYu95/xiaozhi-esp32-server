package xiaozhi.modules.parent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "生成设备邀请")
public class DeviceInviteCreateDTO {
    @Schema(description = "设备 ID（MAC）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String deviceId;
}
