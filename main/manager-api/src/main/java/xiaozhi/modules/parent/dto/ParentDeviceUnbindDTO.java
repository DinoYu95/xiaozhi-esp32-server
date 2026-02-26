package xiaozhi.modules.parent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "家长端解绑设备请求")
public class ParentDeviceUnbindDTO {
    @Schema(description = "设备 id（mac）")
    private String deviceId;
}
