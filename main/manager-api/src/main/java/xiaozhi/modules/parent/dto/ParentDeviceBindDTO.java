package xiaozhi.modules.parent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "家长端绑定设备请求")
public class ParentDeviceBindDTO {
    @Schema(description = "6位绑定码")
    private String code;
}
